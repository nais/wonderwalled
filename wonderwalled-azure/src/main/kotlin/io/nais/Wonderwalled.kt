package io.nais

import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AppConfig
import io.nais.common.AuthClient
import io.nais.common.IdentityProvider
import io.nais.common.NaisAuth
import io.nais.common.bearerToken
import io.nais.common.commonSetup
import io.nais.common.requestHeaders

fun main() {
    val config = AppConfig()

    embeddedServer(CIO, port = config.port) {
        commonSetup()

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
                    val exchange = azure.exchange(target, token)
                    call.respond(exchange)
                }

                get("m2m") {
                    val audience = call.request.queryParameters["aud"]
                    if (audience == null) {
                        call.respond(HttpStatusCode.BadRequest, "missing 'aud' query parameter")
                        return@get
                    }

                    val target = audience.toString()
                    val token = azure.token(target)
                    call.respond(token)
                }
            }
        }
    }.start(wait = true)
}

private fun String.toScope(): String = "api://${this.replace(":", ".")}/.default"
