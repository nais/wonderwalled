package io.nais

import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.NaisAuth
import io.nais.common.TokenResponse
import io.nais.common.requestHeaders
import io.nais.common.server

fun main() {
    server { config ->
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
                    when (val response = maskinporten.token(target)) {
                        is TokenResponse.Success -> call.respond(response)
                        is TokenResponse.Error -> call.respond(response.status, response.error)
                    }
                }

                get("introspect") {
                    val target = call.request.queryParameters["scope"] ?: "nav:test/api"
                    when (val response = maskinporten.token(target)) {
                        is TokenResponse.Success -> {
                            val introspection = maskinporten.introspect(response.accessToken)
                            call.respond(introspection)
                        }
                        is TokenResponse.Error -> {
                            call.respond(response.status, response.error)
                        }
                    }
                }

                get("*") {
                    call.respondRedirect("/api/introspect")
                }
            }
        }
    }.start(wait = true)
}
