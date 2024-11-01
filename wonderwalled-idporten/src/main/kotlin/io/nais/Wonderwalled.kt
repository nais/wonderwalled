package io.nais

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.nais.common.bearerToken
import io.nais.common.commonSetup
import io.nais.common.getTokenInfo
import io.nais.common.requestHeaders
import java.net.URI
import java.util.concurrent.TimeUnit

fun main() {
    val config = Configuration()

    embeddedServer(CIO, port = config.port) {
        wonderwalled(config)
    }.start(wait = true)
}

fun Application.wonderwalled(config: Configuration) {
    val jwksURL = URI.create(config.idporten.openIdConfiguration.jwksUri).toURL()
    val jwkProvider =
        JwkProviderBuilder(jwksURL)
            .cached(10, 1, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    commonSetup()

    val tokenXClient = TokenXClient(config.tokenx)

    val texasClient = TexasClient(config.texas)

    authentication {
        jwt {
            verifier(jwkProvider, config.idporten.openIdConfiguration.issuer) {
                withClaimPresence("client_id")
                withClaim("client_id", config.idporten.clientId)
            }

            validate { credentials -> JWTPrincipal(credentials.payload) }

            // challenge is called if the request authentication fails or is not provided
            challenge { _, _ ->
                val ingress =
                    config.ingress.ifEmpty(defaultValue = {
                        "${call.request.local.scheme}://${call.request.host()}"
                    })

                // redirect to login endpoint (wonderwall) and indicate that the user should be redirected back
                // to the original request path after authentication
                call.respondRedirect("$ingress/oauth2/login?redirect=${call.request.uri}")
            }
        }
    }

    routing {
        authenticate {
            route("api") {
                get("headers") {
                    call.respond(call.requestHeaders())
                }

                get("me") {
                    when (val tokenInfo = call.getTokenInfo()) {
                        null -> call.respond(HttpStatusCode.Unauthorized, "Could not find a valid principal")
                        else -> call.respond(tokenInfo)
                    }
                }
                get("obo") {
                    val token = call.bearerToken()
                    if (token == null) {
                        call.respond(HttpStatusCode.Unauthorized, "missing bearer token in Authorization header")
                        return@get
                    }

                    val audience = call.request.queryParameters["aud"]
                    if (audience == null) {
                        call.respond(HttpStatusCode.BadRequest, "missing 'aud' query parameter")
                        return@get
                    }

                    try {
                        val oboToken = tokenXClient.getOnBehalfOfAccessToken(audience, token)
                        call.respond(oboToken)
                    } catch (e: ClientRequestException) {
                        call.respondBytes(e.response.readBytes(), e.response.contentType(), e.response.status)
                    }
                }
            }
        }

        route("api") {
            get("maskinporten") {
                call.respond(
                    texasClient.introspect(
                        texasClient.token(call.request.queryParameters["target"] ?: "nav:test/api").accessToken
                    )
                )
            }
        }
    }
}