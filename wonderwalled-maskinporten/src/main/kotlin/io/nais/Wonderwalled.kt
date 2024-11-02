package io.nais

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.IdentityProvider
import io.nais.common.TexasClient
import io.nais.common.commonSetup
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
    val jwksURL = URI.create(config.azure.openIdConfiguration.jwksUri).toURL()
    val jwkProvider =
        JwkProviderBuilder(jwksURL)
            .cached(10, 1, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    commonSetup(withRootHandler = false)

    val texasClient = TexasClient(config.texas, IdentityProvider.MASKINPORTEN)

    authentication {
        jwt {
            verifier(jwkProvider, config.azure.openIdConfiguration.issuer) {
                withAudience(config.azure.clientId)
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
        get("/") {
            call.respondRedirect("/api/maskinporten")
        }
        authenticate {
            route("api") {
                get("headers") {
                    call.respond(call.requestHeaders())
                }

                get("maskinporten") {
                    call.respond(
                        texasClient.introspect(
                            texasClient.token(call.request.queryParameters["target"] ?: "nav:test/api").accessToken,
                        ),
                    )
                }
                get("*") {
                    call.respondRedirect("/")
                }
            }
        }
    }
}
