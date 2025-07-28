package io.nais

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
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
        val azure = AuthClient(config.auth, IdentityProvider.AZURE_AD)

        install(Authentication) {
            texas {
                client = azure
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
                        val principal = call.principal<TexasPrincipal>()
                        if (principal == null) {
                            call.respond(HttpStatusCode.Unauthorized, "missing principal")
                            return@get
                        }

                        val audience = call.request.queryParameters["aud"]
                        if (audience == null) {
                            call.respond(HttpStatusCode.BadRequest, "missing 'aud' query parameter")
                            return@get
                        }

                        val target = audience.toScope()
                        when (val response = azure.exchange(target, principal.token)) {
                            is TokenResponse.Success -> call.respond(response)
                            is TokenResponse.Error -> call.respond(response.status, response.error)
                        }
                    }

                    get("m2m") {
                        val audience = call.request.queryParameters["aud"]
                        if (audience == null) {
                            call.respond(HttpStatusCode.BadRequest, "missing 'aud' query parameter")
                            return@get
                        }

                        val target = audience.toScope()
                        when (val response = azure.token(target)) {
                            is TokenResponse.Success -> call.respond(response)
                            is TokenResponse.Error -> call.respond(response.status, response.error)
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}

private fun String.toScope(): String =
    if (this.startsWith("https://") || this.startsWith("api://")) {
        this
    } else {
        "api://${this.replace(":", ".")}/.default"
    }
