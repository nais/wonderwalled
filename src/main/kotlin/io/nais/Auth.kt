package io.nais

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.request.path
import io.ktor.response.respondRedirect
import java.net.URL
import java.util.concurrent.TimeUnit

internal fun Application.jwtAuthentication(config: Configuration) {
    val jwkProvider = JwkProviderBuilder(URL(config.openid.openIdConfiguration.jwksUri))
        .cached(10, 1, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt {
            verifier(jwkProvider, config.openid.openIdConfiguration.issuer) {
                withClaimPresence("client_id")
                withClaim("client_id", config.openid.clientId)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
            challenge { _,_ ->
                val requestPath = call.request.path()
                call.respondRedirect("${config.wonderwall.url}/oauth2/login?redirect=$requestPath")
            }
        }
    }
}
