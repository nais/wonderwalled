package io.nais

import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.Config
import io.nais.common.IdentityProvider
import io.nais.common.TexasPrincipal
import io.nais.common.TokenResponse
import io.nais.common.defaultHttpClient
import io.nais.common.installDefaults
import io.nais.common.requestHeaders
import io.nais.common.start
import io.nais.common.texas

fun main() {
    start(Application::idporten)
}

fun Application.idporten(
    config: Config,
    httpClient: HttpClient = defaultHttpClient(),
) {
    installDefaults()

    val tokenx = AuthClient(config.auth, IdentityProvider.TOKENX, httpClient)
    val idporten = AuthClient(config.auth, IdentityProvider.IDPORTEN, httpClient)

    install(Authentication) {
        texas {
            client = idporten
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

                    when (val response = tokenx.exchange(audience, principal.token)) {
                        is TokenResponse.Success -> call.respond(response)
                        is TokenResponse.Error -> call.respond(response.status, response.error)
                    }
                }
            }
        }
    }
}
