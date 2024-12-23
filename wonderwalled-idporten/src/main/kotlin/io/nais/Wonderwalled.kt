package io.nais

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.NaisAuth
import io.nais.common.TokenResponse
import io.nais.common.bearerToken
import io.nais.common.requestHeaders
import io.nais.common.server

fun main() {
    server { config ->
        val tokenx = AuthClient(config.auth, IdentityProvider.TOKEN_X)
        val idporten = AuthClient(config.auth, IdentityProvider.IDPORTEN)

        routing {
            route("api") {
                install(NaisAuth) {
                    client = idporten
                    ingress = config.ingress
                }

                get("headers") {
                    call.respond(call.requestHeaders())
                }

                get("me") {
                    val token = call.bearerToken()
                    if (token == null) {
                        call.respond(HttpStatusCode.Unauthorized, "missing bearer token in Authorization header")
                        return@get
                    }

                    val introspection = idporten.introspect(token)
                    call.respond(introspection)
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
    }.start(wait = true)
}
