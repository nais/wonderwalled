package io.nais

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.nais.common.IdentityProvider
import io.nais.common.TexasClient
import io.nais.common.TexasConfiguration
import io.nais.common.TexasValidator
import io.nais.common.buildOpenTelemetryConfig
import io.nais.common.commonSetup
import io.nais.common.requestHeaders
import io.opentelemetry.instrumentation.ktor.v3_0.server.KtorServerTracing

private val config =
    systemProperties() overriding
        EnvironmentVariables()

data class Configuration(
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val texas: TexasConfiguration = TexasConfiguration(config),
    // optional, generally only needed when running locally
    val ingress: String =
        config.getOrElse(
            key = Key("wonderwall.ingress", stringType),
            default = "",
        ),
)

fun main() {
    val config = Configuration()

    embeddedServer(CIO, port = config.port) {
        wonderwalled(config)
    }.start(wait = true)
}

fun Application.wonderwalled(config: Configuration) {
    commonSetup()

    val otel = buildOpenTelemetryConfig("wonderwalled-maskinporten")

    install(KtorServerTracing) {
        setOpenTelemetry(otel)
    }

    val texasClient = TexasClient(config.texas, IdentityProvider.MASKINPORTEN, otel)

    routing {
        route("api") {
            install(TexasValidator) {
                client = TexasClient(config.texas, IdentityProvider.AZURE_AD, otel)
                ingress = config.ingress
            }

            get("headers") {
                call.respond(call.requestHeaders())
            }

            get("token") {
                call.respond(texasClient.token(call.request.queryParameters["target"] ?: "nav:test/api"))
            }
            get("introspect") {
                call.respond(
                    texasClient.introspect(
                        texasClient.token(call.request.queryParameters["target"] ?: "nav:test/api").accessToken,
                    ),
                )
            }
            get("*") {
                call.respondRedirect("/api/introspect")
            }
        }
    }
}
