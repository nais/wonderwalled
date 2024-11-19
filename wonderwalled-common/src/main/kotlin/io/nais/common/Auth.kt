package io.nais.common

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class IdentityProvider(@JsonValue val alias: String) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
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

data class TokenRequest(
    val target: String,
    @JsonProperty("identity_provider")
    val identityProvider: IdentityProvider,
)

data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresInSeconds: Int,
)

data class TokenExchangeRequest(
    val target: String,
    @JsonProperty("identity_provider")
    val identityProvider: IdentityProvider,
    @JsonProperty("user_token")
    val userToken: String,
)

data class TokenIntrospectionRequest(
    val token: String,
    @JsonProperty("identity_provider")
    val identityProvider: IdentityProvider,
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
    private val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
    private val httpClient: HttpClient = defaultHttpClient(openTelemetry),
) {
    private val tracer: Tracer = openTelemetry.getTracer("io.nais.common.AuthClient")

    suspend fun token(target: String) =
        tracer.withSpan("auth/token", parameters = {
            setAllAttributes(traceAttributes(target))
        }) {
            httpClient.post(config.tokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(target, provider))
            }.body<TokenResponse>()
        }

    suspend fun exchange(target: String, userToken: String) =
        tracer.withSpan("auth/exchange", parameters = {
            setAllAttributes(traceAttributes(target))
        }) {
            httpClient.post(config.tokenExchangeEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TokenExchangeRequest(target, provider, userToken))
            }.body<TokenResponse>()
        }

    suspend fun introspect(accessToken: String) =
        tracer.withSpan("auth/introspect", parameters = {
            setAllAttributes(traceAttributes())
        }) {
            httpClient.post(config.tokenIntrospectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TokenIntrospectionRequest(accessToken, provider))
            }.body<TokenIntrospectionResponse>()
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

// TODO: remove all code below when replaced with Texas
const val WELL_KNOWN_PATH = "/.well-known"
const val OPENID_CONFIGURATION_PATH = "/openid-configuration"
const val OAUTH_AUTHORIZATION_SERVER_PATH = "/oauth-authorization-server"
const val WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER_PATH = WELL_KNOWN_PATH + OAUTH_AUTHORIZATION_SERVER_PATH
const val WELL_KNOWN_OPENID_CONFIGURATION_PATH = WELL_KNOWN_PATH + OPENID_CONFIGURATION_PATH

data class OpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
) {
    fun validate(wellKnownConfigurationUrl: String) {
        val resolvedOpenIDAuthority: String = this.issuer.removeSuffix("/") + WELL_KNOWN_OPENID_CONFIGURATION_PATH
        val resolvedOAuthAuthority: String = this.issuer.removeSuffix("/") + WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER_PATH

        if (resolvedOpenIDAuthority != wellKnownConfigurationUrl && resolvedOAuthAuthority != wellKnownConfigurationUrl) {
            throw invalidOpenIdConfigurationException(
                expected = listOf(resolvedOpenIDAuthority, resolvedOAuthAuthority),
                got = wellKnownConfigurationUrl,
            )
        }
    }
}

private fun invalidOpenIdConfigurationException(
    expected: List<String>,
    got: String,
): RuntimeException =
    RuntimeException("authority does not match the issuer returned by provider: got $got, expected one of $expected")

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("token_type")
    val tokenType: String,
)
