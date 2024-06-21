package io.nais

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.host
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.bearerToken
import io.nais.common.commonSetup
import io.nais.common.getTokenInfo
import io.nais.common.requestHeaders
import java.net.URI
import java.util.concurrent.TimeUnit

fun main() {
    val config = Configuration()

    embeddedServer(CIO, port = config.port) {
        wonderwalled(config)
    }.start(wait = true)
}

fun Application.wonderwalled(config: Configuration) {
    val jwksUrl = URI.create(config.azure.openIdConfiguration.jwksUri).toURL()
    val jwkProvider =
        JwkProviderBuilder(jwksUrl)
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
                val ingress =
                    config.ingress.ifEmpty(defaultValue = {
                        "${call.request.local.scheme}://${call.request.host()}"
                    })

                // redirect to login endpoint (wonderwall) and indicate that the user should be redirected back
                // to the original request path after authentication
                call.respondRedirect("$ingress/oauth2/login?redirect=${call.request.uri}")
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

                get("m2m") {
                    val audience = call.request.queryParameters["aud"]
                    if (audience == null) {
                        call.respond(HttpStatusCode.BadRequest, "missing 'aud' query parameter")
                        return@get
                    }

                    try {
                        val token = azureAdClient.getMachineToMachineAccessToken(audience)
                        call.respond(token)
                    } catch (e: ClientRequestException) {
                        call.respondBytes(e.response.readBytes(), e.response.contentType(), e.response.status)
                    }
                }
            }
        }
    }
}
