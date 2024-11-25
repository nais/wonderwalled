package io.nais.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.authorization
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.client.KtorClientTracing
import io.opentelemetry.instrumentation.ktor.v3_0.server.KtorServerTracing
import org.slf4j.event.Level
import java.util.UUID
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

fun defaultHttpClient() = HttpClient(ClientCIO) {
    expectSuccess = true
    install(ClientContentNegotiation) {
        jackson {
            deserializationConfig.apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    install(KtorClientTracing) {
        setOpenTelemetry(GlobalOpenTelemetry.get())
    }
}

fun server(
    config: Config = Config(),
    module: Application.(Config) -> Unit
) = embeddedServer(ServerCIO, port = config.port) {
    install(ServerContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(IgnoreTrailingSlash)

    install(CallId) {
        header(HttpHeaders.XCorrelationId)
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
    }

    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        filter { call -> !call.request.path().startsWith("/internal") }
        callIdMdc("call_id")
    }

    install(KtorServerTracing) {
        setOpenTelemetry(GlobalOpenTelemetry.get())
    }

    routing {
        get("/") {
            call.respondRedirect("/api/me")
        }

        route("internal") {
            get("is_alive") {
                call.respond<String>("alive")
            }
            get("is_ready") {
                call.respond<String>("ready")
            }
        }
    }

    module(config)
}

fun ApplicationCall.requestHeaders(): Map<String, String> =
    request
        .headers
        .entries()
        .associate { header -> header.key to header.value.joinToString() }

fun ApplicationCall.bearerToken(): String? =
    request.authorization()
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
