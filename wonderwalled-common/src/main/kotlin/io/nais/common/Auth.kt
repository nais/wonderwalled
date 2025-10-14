package io.nais.common

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class IdentityProvider(
    @JsonValue val alias: String,
) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    TOKEN_X("tokenx"),
}

sealed class TokenResponse {
    data class Success(
        @param:JsonProperty("access_token")
        val accessToken: String,
        @param:JsonProperty("expires_in")
        val expiresInSeconds: Int,
    ) : TokenResponse()

    data class Error(
        val error: TokenErrorResponse,
        val status: HttpStatusCode,
    ) : TokenResponse()
}

data class TokenErrorResponse(
    val error: String,
    @param:JsonProperty("error_description")
    val errorDescription: String,
)

/**
 * TokenIntrospectionResponse represents the JSON response from the token introspection endpoint.
 *
 * The `other` field is a generic map that contains an arbitrary combination of additional claims contained in the token.
 *
 * TODO(user): If you know the exact claims to expect, you should instead explicitly define these as fields on the
 *  data class itself and ignore everything else, for example:
 *
 * ```kotlin
 * data class TokenIntrospectionResponse(
 *   val active: Boolean,
 *   @JsonInclude(JsonInclude.Include.NON_NULL)
 *   val error: String?,
 *   val sub: String,
 *   @JsonProperty("preferred_username")
 *   val username: String,
 *   val exp: Long,
 * )
 * ```
 */
data class TokenIntrospectionResponse(
    val active: Boolean,
    @param:JsonInclude(JsonInclude.Include.NON_NULL)
    val error: String?,
    @param:JsonAnySetter @get:JsonAnyGetter
    val other: Map<String, Any?> = mutableMapOf(),
)

class AuthClient(
    private val config: Config.Auth,
    private val provider: IdentityProvider,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    suspend fun token(target: String): TokenResponse =
        try {
            httpClient
                .submitForm(
                    config.tokenEndpoint,
                    parameters {
                        set("target", target)
                        set("identity_provider", provider.alias)
                        set("skip_cache", "true")
                    },
                ).body<TokenResponse.Success>()
        } catch (e: ResponseException) {
            TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
        }

    suspend fun exchange(
        target: String,
        userToken: String,
    ): TokenResponse =
        try {
            httpClient
                .submitForm(
                    config.tokenExchangeEndpoint,
                    parameters {
                        set("target", target)
                        set("user_token", userToken)
                        set("identity_provider", provider.alias)
                        set("skip_cache", "true")
                    },
                ).body<TokenResponse.Success>()
        } catch (e: ResponseException) {
            TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
        }

    suspend fun introspect(accessToken: String): TokenIntrospectionResponse =
        httpClient
            .submitForm(
                config.tokenIntrospectionEndpoint,
                parameters {
                    set("token", accessToken)
                    set("identity_provider", provider.alias)
                },
            ).body()
}

fun AuthenticationConfig.texas(
    name: String? = null,
    configure: TexasAuthenticationProvider.Config.() -> Unit,
) {
    register(TexasAuthenticationProvider.Config(name).apply(configure).build())
}

/**
 * TexasAuthenticationProvider is an [io.ktor.server.auth.AuthenticationProvider] that requires Bearer tokens in the
 * Authorization header for incoming HTTP requests. It validates these tokens by using Texas's introspection endpoint.
 *
 * Invalid credentials result in a HTTP redirect to login endpoint provided by Wonderwall.
 * This makes sense if Wonderwall runs in front of this application, i.e. if the frontend is served by this application.
 *
 * If this is a standalone API, you want to respond with an HTTP 401 Unauthorized instead. This allows
 * the calling client, such as a backend-for-frontend, to appropriately handle the response instead of redirecting
 * a resource request which often results in a CORS error. For standalone APIs, consider implementing authentication
 * with [io.ktor.server.auth.bearer] instead. For example (simplified, with error handling and logging omitted):
 *
 * ```kotlin
 * install(Authentication) {
 *   bearer("some-provider") {
 *     authenticate { credentials ->
 *       val introspectResponse = authClient.introspect(credentials.token)
 *       return@authenticate if (introspectResponse.active) {
 *         TexasPrincipal(
 *           claims = introspectResponse.claims,
 *           token = credentials.token,
 *         )
 *       } else {
 *         null
 *       }
 *     }
 *   }
 * }
 * ```
 */
class TexasAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(
        name: String?,
    ) : AuthenticationProvider.Config(name) {
        lateinit var client: AuthClient
        var logger: Logger = LoggerFactory.getLogger("io.nais.common.TexasAuthenticationProvider")
        var ingress: String = ""

        internal fun build() = TexasAuthenticationProvider(this)
    }

    private val client = config.client
    private val logger = config.logger
    private val ingress = config.ingress

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val applicationCall = context.call
        val token =
            (applicationCall.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
                ?.takeIf { header -> header.authScheme.lowercase() == AuthScheme.Bearer.lowercase() }
                ?.blob

        if (token == null) {
            logger.warn("unauthenticated: no Bearer token found in Authorization header")
            context.loginChallenge(AuthenticationFailedCause.NoCredentials)
            return
        }

        val introspectResponse =
            try {
                client.introspect(token)
            } catch (e: Exception) {
                // TODO(user): You should handle the specific exceptions that can be thrown by the HTTP client, e.g. retry on network errors and so on
                logger.error("unauthenticated: introspect request failed", e)
                context.loginChallenge(AuthenticationFailedCause.Error(e.message ?: "introspect request failed"))
                return
            }

        if (!introspectResponse.active) {
            logger.warn("unauthenticated: ${introspectResponse.error}")
            context.loginChallenge(AuthenticationFailedCause.InvalidCredentials)
            return
        }

        logger.info("authenticated - claims='${introspectResponse.other}'")
        context.principal(
            TexasPrincipal(
                claims = introspectResponse.other,
                token = token,
            ),
        )
    }

    private fun AuthenticationContext.loginChallenge(cause: AuthenticationFailedCause) {
        challenge("Texas", cause) { authenticationProcedureChallenge, call ->
            val target = call.loginUrl()
            logger.info("unauthenticated: redirecting to '$target'")
            call.respondRedirect(target)
            authenticationProcedureChallenge.complete()
        }
    }

    /**
     * loginUrl constructs a URL string that points to the login endpoint (Wonderwall) for redirecting a request.
     * It also indicates that the user should be redirected back to the original request path after authentication
     */
    private fun ApplicationCall.loginUrl(): String {
        val host =
            ingress.ifEmpty(defaultValue = {
                "${this.request.local.scheme}://${this.request.host()}"
            })

        return "$host/oauth2/login?redirect=${this.request.uri}"
    }
}

/**
 * TexasPrincipal represents the authenticated principal.
 * The `claims` field is a map of arbitrary claims from the [TokenIntrospectionResponse].
 *
 * TODO(user): If you know the exact claims to expect, you should instead explicitly define these as fields on the
 *  data class itself and ignore everything else, for example:
 *
 * ```kotlin
 * data class TexasPrincipal(
 *   val claims: Claims,
 *   val token: String,
 * )
 *
 * data class Claims(
 *   val subject: String,
 *   val email String,
 *   val familyName: String,
 *   val givenName: String,
 *   ...
 * )
 * ```
 */
data class TexasPrincipal(
    val claims: Map<String, Any?>,
    val token: String,
)
