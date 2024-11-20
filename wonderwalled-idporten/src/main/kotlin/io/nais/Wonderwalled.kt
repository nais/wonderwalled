package io.nais

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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

                    try {
                        val exchange = tokenx.exchange(audience, token)
                        call.respond(exchange)
                    } catch (e: ClientRequestException) {
                        call.respondBytes(e.response.readRawBytes(), e.response.contentType(), e.response.status)
                    }
                }
            }
        }
    }.start(wait = true)
}
