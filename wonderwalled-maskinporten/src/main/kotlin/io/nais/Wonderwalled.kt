package io.nais

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.AuthClient
import io.nais.common.AuthClientConfig
import io.nais.common.IdentityProvider
import io.nais.common.NaisAuth
import io.nais.common.TokenIntrospectionResponse
import io.nais.common.TokenResponse
import io.nais.common.commonSetup
import io.nais.common.openTelemetry
import io.nais.common.requestHeaders
import io.opentelemetry.instrumentation.ktor.v3_0.server.KtorServerTracing

private val config =
    systemProperties() overriding
        EnvironmentVariables()

data class Configuration(
    val name: String = "wonderwalled-maskinporten",
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val auth: AuthClientConfig = AuthClientConfig(config),
    // optional, generally only needed when running locally
    val ingress: String = config.getOrElse(key = Key("login.ingress", stringType), default = ""),
)

fun main() {
    val config = Configuration()

    embeddedServer(CIO, port = config.port) {
        commonSetup()

        val otel = openTelemetry(config.name)
        val auth = AuthClient(config.auth, IdentityProvider.MASKINPORTEN, otel)

        install(KtorServerTracing) {
            setOpenTelemetry(otel)
        }

        routing {
            route("api") {
                install(NaisAuth) {
                    client = AuthClient(config.auth, IdentityProvider.AZURE_AD, otel)
                    ingress = config.ingress
                }

                get("headers") {
                    call.respond<Map<String, String>>(call.requestHeaders())
                }

                get("token") {
                    call.respond<TokenResponse>(auth.token(call.request.queryParameters["target"] ?: "nav:test/api"))
                }

                get("introspect") {
                    call.respond<TokenIntrospectionResponse>(
                        auth.introspect(
                            auth.token(call.request.queryParameters["target"] ?: "nav:test/api").accessToken,
                        ),
                    )
                }

                get("*") {
                    call.respondRedirect("/api/introspect")
                }
            }
        }
    }.start(wait = true)
}
