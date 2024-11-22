package io.nais.common

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class IdentityProvider(@JsonValue val alias: String) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    TOKEN_X("tokenx"),
}

data class AuthClientConfig(
    val tokenEndpoint: String,
    val tokenExchangeEndpoint: String,
    val tokenIntrospectionEndpoint: String,
) {
    constructor(config: Configuration) : this(
        tokenEndpoint = config[Key("nais.token.endpoint", stringType)],
        tokenExchangeEndpoint = config[Key("nais.token.exchange.endpoint", stringType)],
        tokenIntrospectionEndpoint = config[Key("nais.token.introspection.endpoint", stringType)],
    )
}

data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresInSeconds: Int,
)

data class TokenIntrospectionResponse(
    val active: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val error: String?,
    @JsonAnySetter @get:JsonAnyGetter
    val other: Map<String, Any?> = mutableMapOf(),
)

class AuthClient(
    private val config: AuthClientConfig,
    private val provider: IdentityProvider,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private val tracer: Tracer = GlobalOpenTelemetry.get().getTracer("io.nais.common.AuthClient")

    suspend fun token(target: String) =
        tracer.withSpan("AuthClient/token (${provider.alias})", parameters = {
            setAllAttributes(traceAttributes(target))
        }) {
            httpClient.submitForm(config.tokenEndpoint, parameters {
                set("target", target)
                set("identity_provider", provider.alias)
            }).body<TokenResponse>()
        }

    suspend fun exchange(target: String, userToken: String) =
        tracer.withSpan("AuthClient/exchange (${provider.alias})", parameters = {
            setAllAttributes(traceAttributes(target))
        }) {
            httpClient.submitForm(config.tokenExchangeEndpoint, parameters {
                set("target", target)
                set("user_token", userToken)
                set("identity_provider", provider.alias)
            }).body<TokenResponse>()
        }

    suspend fun introspect(accessToken: String) =
        tracer.withSpan("AuthClient/introspect (${provider.alias})", parameters = {
            setAllAttributes(traceAttributes())
        }) {
            httpClient.submitForm(config.tokenIntrospectionEndpoint, parameters {
                set("token", accessToken)
                set("identity_provider", provider.alias)
            }).body<TokenIntrospectionResponse>()
        }

    private fun traceAttributes(target: String? = null) = Attributes.builder().apply {
        put(attributeKeyIdentityProvider, provider.alias)
        if (target != null) {
            put(attributeKeyTarget, target)
        }
    }.build()

    companion object {
        private val attributeKeyTarget: AttributeKey<String> = AttributeKey.stringKey("target")
        private val attributeKeyIdentityProvider: AttributeKey<String> = AttributeKey.stringKey("identity_provider")
    }
}

class AuthPluginConfiguration(
    var client: AuthClient? = null,
    var ingress: String? = null,
    var logger: Logger = LoggerFactory.getLogger("io.nais.common.ktor.NaisAuth"),
)

val NaisAuth = createRouteScopedPlugin(
    name = "NaisAuth",
    createConfiguration = ::AuthPluginConfiguration,
) {
    val logger = pluginConfig.logger
    val client = pluginConfig.client ?: throw IllegalArgumentException("NaisAuth plugin: client must be set")
    val ingress = pluginConfig.ingress ?: ""

    val challenge: suspend (ApplicationCall) -> Unit = { call ->
        val target = call.loginUrl(ingress)
        logger.info("unauthenticated: redirecting to '$target'")
        call.respondRedirect(target)
    }

    pluginConfig.apply {
        onCall { call ->
            val token = call.bearerToken()
            if (token == null) {
                logger.warn("unauthenticated: no Bearer token found in Authorization header")
                challenge(call)
                return@onCall
            }

            val introspectResponse = try {
                client.introspect(token)
            } catch (e: Exception) {
                logger.error("unauthenticated: introspect request failed: ${e.message}")
                challenge(call)
                return@onCall
            }

            if (introspectResponse.active) {
                logger.info("authenticated - claims='${introspectResponse.other}'")
                return@onCall
            }

            logger.warn("unauthenticated: ${introspectResponse.error}")
            challenge(call)
            return@onCall
        }
    }

    logger.info("NaisAuth plugin loaded.")
}

// loginUrl constructs a URL string that points to the login endpoint (Wonderwall) for redirecting a request.
// It also indicates that the user should be redirected back to the original request path after authentication
private fun ApplicationCall.loginUrl(defaultHost: String): String {
    val host = defaultHost.ifEmpty(defaultValue = {
        "${this.request.local.scheme}://${this.request.host()}"
    })

    return "$host/oauth2/login?redirect=${this.request.uri}"
}