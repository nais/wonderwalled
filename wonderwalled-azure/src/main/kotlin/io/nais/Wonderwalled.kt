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
        val azure = AuthClient(config.auth, IdentityProvider.AZURE_AD)

        routing {
            route("api") {
                install(NaisAuth) {
                    client = azure
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

                    val introspection = azure.introspect(token)
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

                    val target = audience.toScope()
                    when (val response = azure.exchange(target, token)) {
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
    }.start(wait = true)
}

private fun String.toScope(): String = "api://${this.replace(":", ".")}/.default"
