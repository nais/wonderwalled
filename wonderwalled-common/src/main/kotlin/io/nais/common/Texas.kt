package io.nais.common

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class IdentityProvider(
    val alias: String,
) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
}

data class TexasConfiguration(
    val tokenEndpoint: String,
    val tokenExchangeEndpoint: String,
    val introspectionEndpoint: String,
) {
    constructor(config: Configuration) : this(
        tokenEndpoint = config[Key("texas.token.endpoint", stringType)],
        tokenExchangeEndpoint = config[Key("texas.token.exchange.endpoint", stringType)],
        introspectionEndpoint = config[Key("texas.introspection.endpoint", stringType)],
    )
}

data class TexasTokenRequest(
    val target: String,
    @JsonProperty("identity_provider")
    val identityProvider: String,
)

data class TexasTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
)

data class TexasTokenExchangeRequest(
    @JsonProperty("user_token")
    val userToken: String,
    @JsonProperty("identity_provider")
    val identityProvider: String,
)

data class TexasIntrospectionRequest(
    val token: String,
)

data class TexasIntropectionResponse(
    val active: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val error: String?,
    @JsonAnySetter @get:JsonAnyGetter
    val other: Map<String, Any?> = mutableMapOf(),
)

class TexasClient(
    private val config: TexasConfiguration,
    private val provider: IdentityProvider,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    suspend fun token(target: String) =
        httpClient
            .post(config.tokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasTokenRequest(target, provider.alias))
            }.body<TexasTokenResponse>()

    suspend fun exchange(userToken: String) =
        httpClient
            .post(config.tokenExchangeEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasTokenExchangeRequest(userToken, provider.alias))
            }.body<TexasTokenResponse>()

    suspend fun introspect(accessToken: String) =
        httpClient
            .post(config.introspectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasIntrospectionRequest(accessToken))
            }.body<TexasIntropectionResponse>()
}

class TexasPluginConfiguration(
    var client: TexasClient? = null,
    var ingress: String? = null,
    var logger: Logger = LoggerFactory.getLogger("io.nais.common.TexasValidatorPlugin"),
)

val TexasValidator =
    createRouteScopedPlugin(
        name = "TexasValidatorPlugin",
        createConfiguration = ::TexasPluginConfiguration,
    ) {
        val logger = pluginConfig.logger
        val client = pluginConfig.client ?: throw IllegalArgumentException("TexasValidator plugin: client must be set")
        val ingress = pluginConfig.ingress ?: ""

        val challenge: suspend (ApplicationCall) -> Unit = { call ->
            val host =
                ingress.ifEmpty(defaultValue = {
                    "${call.request.local.scheme}://${call.request.host()}"
                })
            // redirect to login endpoint (wonderwall) and indicate that the user should be redirected back
            // to the original request path after authentication
            val target = "$host/oauth2/login?redirect=${call.request.uri}"
            logger.info("unauthenticated: redirecting to '$target'")
            call.respondRedirect(target)
        }

        pluginConfig.apply {
            onCall { call ->
                call.bearerToken()?.let { token ->
                    val introspectResponse =
                        try {
                            client.introspect(token)
                        } catch (e: ClientRequestException) {
                            logger.error("unauthenticated: introspect request failed: ${e.message}")
                            challenge(call)
                            return@onCall
                        }

                    // FIXME: how do we check that the token was issued by the correct identity provider?
                    if (introspectResponse.active) {
                        logger.info("authenticated - claims='${introspectResponse.other}'")
                        return@onCall
                    }

                    logger.warn("unauthenticated: ${introspectResponse.error}")
                    challenge(call)
                    return@onCall
                }

                logger.warn("unauthenticated: no Bearer token found in Authorization header")
                challenge(call)
                return@onCall
            }
        }

        logger.info("TexasValidator plugin loaded.")
    }
