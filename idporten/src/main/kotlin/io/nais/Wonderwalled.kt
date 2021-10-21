package io.nais

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.host
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.IgnoreTrailingSlash
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.event.Level
import java.net.URL
import java.util.concurrent.TimeUnit

internal val httpClient = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

fun main() {
    val config = Configuration()
    val jwkProvider = JwkProviderBuilder(URL(config.idporten.openIdConfiguration.jwksUri))
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    embeddedServer(Netty, port = config.port) {
        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
        install(IgnoreTrailingSlash)
        install(CallLogging) {
            level = Level.INFO
        }

        authentication {
            jwt {
                verifier(jwkProvider, config.idporten.openIdConfiguration.issuer) {
                    withClaimPresence("client_id")
                    withClaim("client_id", config.idporten.clientId)
                }
                validate { credentials -> JWTPrincipal(credentials.payload) }
                challenge { _, _ ->
                    val ingress = config.ingress.ifEmpty {
                        "${call.request.local.scheme}://${call.request.host()}"
                    }
                    call.respondRedirect("$ingress/oauth2/login?redirect=${call.request.path()}")
                }
            }
        }

        routing {
            route("internal") {
                get("is_alive") {
                    call.respond("alive")
                }
                get("is_ready") {
                    call.respond("ready")
                }
            }
            authenticate {
                route("api") {
                    get("headers") {
                        call.respond(call.requestHeaders())
                    }

                    get("me") {
                        when (val tokenInfo = call.getTokenInfo()) {
                            null -> call.respond(HttpStatusCode.Unauthorized, "Could not find a valid principal")
                            else -> call.respond(tokenInfo)
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}
