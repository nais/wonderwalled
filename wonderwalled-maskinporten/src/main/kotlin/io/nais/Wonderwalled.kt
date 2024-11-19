package io.nais

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AppConfig
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.NaisAuth
import io.nais.common.commonSetup
import io.nais.common.requestHeaders

fun main() {
    val config = AppConfig()

    embeddedServer(CIO, port = config.port) {
        commonSetup()

        val auth = AuthClient(config.auth, IdentityProvider.MASKINPORTEN)

        routing {
            route("api") {
                install(NaisAuth) {
                    client = AuthClient(config.auth, IdentityProvider.AZURE_AD)
                    ingress = config.ingress
                }

                get("headers") {
                    call.respond(call.requestHeaders())
                }

                get("token") {
                    call.respond(auth.token(call.request.queryParameters["target"] ?: "nav:test/api"))
                }

                get("introspect") {
                    call.respond(
                        auth.introspect(
                            auth.token(call.request.queryParameters["target"] ?: "nav:test/api").accessToken,
                        ),
                    )
                }

                get("*") {
                    call.respondRedirect("/api/introspect")
                }
            }
        }
    }.start(wait = true)
}
