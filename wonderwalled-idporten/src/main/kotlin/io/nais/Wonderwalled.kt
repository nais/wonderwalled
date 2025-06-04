package io.nais

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.TexasPrincipal
import io.nais.common.TokenResponse
import io.nais.common.bearerToken
import io.nais.common.defaultHttpClient
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

        val httpClient = defaultHttpClient()

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

            route("api/public") {
                post("obo") {
                    val formParameters = call.receiveParameters()

                    val pid = formParameters["pid"]
                    if (pid == null) {
                        call.respond(HttpStatusCode.BadRequest, "missing 'pid' form parameter")
                        return@post
                    }
                    val acr = formParameters["acr"] ?: "idporten-loa-high"
                    if (acr !in listOf("idporten-loa-high", "idporten-loa-substantial")) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "invalid 'acr' form parameter, must be 'idporten-loa-high' or 'idporten-loa-substantial'",
                        )
                        return@post
                    }

                    val token =
                        httpClient
                            .get("https://fakedings.intern.dev.nav.no/fake/idporten") {
                                url {
                                    parameter("pid", pid)
                                    parameter("acr", acr)
                                }
                            }.body<String>()

                    val audience = formParameters["aud"]
                    if (audience == null) {
                        call.respond(HttpStatusCode.BadRequest, "missing 'aud' form parameter")
                        return@post
                    }

                    when (val response = tokenx.exchange(audience, token)) {
                        is TokenResponse.Success -> call.respond(response.accessToken)
                        is TokenResponse.Error -> call.respond(response.status, response.error)
                    }
                }
            }
        }
    }.start(wait = true)
}
