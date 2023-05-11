package io.nais

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.features.ClientRequestException
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.request.host
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.nais.common.bearerToken
import io.nais.common.commonSetup
import io.nais.common.getTokenInfo
import io.nais.common.requestHeaders
import java.net.URL
import java.util.concurrent.TimeUnit

fun main() {
    val config = Configuration()

    embeddedServer(Netty, port = config.port) {
        wonderwalled(config)
    }.start(wait = true)
}

fun Application.wonderwalled(config: Configuration) {
    val jwkProvider = JwkProviderBuilder(URL(config.azure.openIdConfiguration.jwksUri))
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    commonSetup()

    val azureAdClient = AzureAdClient(config.azure)

    authentication {
        jwt {
            verifier(jwkProvider, config.azure.openIdConfiguration.issuer) {
                withAudience(config.azure.clientId)
            }

            validate { credentials -> JWTPrincipal(credentials.payload) }

            // challenge is called if the request authentication fails or is not provided
            challenge { _, _ ->
                val ingress = config.ingress.ifEmpty(defaultValue = {
                    "${call.request.local.scheme}://${call.request.host()}"
                })

                // redirect to login endpoint (wonderwall) and indicate that the user should be redirected back
                // to the original request path after authentication
                call.respondRedirect("$ingress/oauth2/login?redirect=${call.request.path()}")
            }
        }
    }

    routing {
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
                        val oboToken = azureAdClient.getOnBehalfOfAccessToken(audience, token)
                        call.respond(oboToken)
                    } catch (e: ClientRequestException) {
                        call.respondBytes(e.response.readBytes(), e.response.contentType(), e.response.status)
                    }
                }
            }
        }
    }
}
