package io.nais

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.TexasPrincipal
import io.nais.common.TokenResponse
import io.nais.common.requestHeaders
import io.nais.common.server
import io.nais.common.texas

fun main() {
    server { config ->
        val maskinporten = AuthClient(config.auth, IdentityProvider.MASKINPORTEN)

        install(Authentication) {
            texas {
                client = AuthClient(config.auth, IdentityProvider.AZURE_AD)
                ingress = config.ingress
            }
        }

        routing {
            authenticate {
                route("api") {
                    get("headers") {
                        val headers = call.requestHeaders()
                        call.respond(headers)
                    }

                    get("me") {
                        val principal = call.principal<TexasPrincipal>()
                        if (principal == null) {
                            call.respond(HttpStatusCode.Unauthorized, "missing principal")
                            return@get
                        }
                        call.respond(principal.claims)
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
        }
    }.start(wait = true)
}
