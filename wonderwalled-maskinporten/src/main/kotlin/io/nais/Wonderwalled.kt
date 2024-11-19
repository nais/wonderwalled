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

        val maskinporten = AuthClient(config.auth, IdentityProvider.MASKINPORTEN)
        val azure = AuthClient(config.auth, IdentityProvider.AZURE_AD)

        routing {
            route("api") {
                install(NaisAuth) {
                    client = azure
                    ingress = config.ingress
                }

                get("headers") {
                    val headers = call.requestHeaders()
                    call.respond(headers)
                }

                get("token") {
                    val target = call.request.queryParameters["scope"] ?: "nav:test/api"
                    val token = maskinporten.token(target)
                    call.respond(token)
                }

                get("introspect") {
                    val target = call.request.queryParameters["scope"] ?: "nav:test/api"
                    val token = maskinporten.token(target)
                    val introspection = maskinporten.introspect(token.accessToken)
                    call.respond(introspection)
                }

                get("*") {
                    call.respondRedirect("/api/introspect")
                }
            }
        }
    }.start(wait = true)
}
