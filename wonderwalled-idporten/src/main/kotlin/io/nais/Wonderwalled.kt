package io.nais

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.TexasPrincipal
import io.nais.common.TokenResponse
import io.nais.common.bearerToken
import io.nais.common.requestHeaders
import io.nais.common.server
import io.nais.common.texas

fun main() {
    server { config ->
        val tokenx = AuthClient(config.auth, IdentityProvider.TOKEN_X)

        install(Authentication) {
            texas {
                client = AuthClient(config.auth, IdentityProvider.IDPORTEN)
                ingress = config.ingress
            }
        }

        routing {
            authenticate {
                route("api") {
                    get("headers") {
                        call.respond(call.requestHeaders())
                    }

                    get("me") {
                        val principal = call.principal<TexasPrincipal>()
                        if (principal == null) {
                            call.respond(HttpStatusCode.Unauthorized, "missing principal")
                            return@get
                        }
                        call.respond(principal.claims)
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

                        when (val response = tokenx.exchange(audience, token)) {
                            is TokenResponse.Success -> call.respond(response)
                            is TokenResponse.Error -> call.respond(response.status, response.error)
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
