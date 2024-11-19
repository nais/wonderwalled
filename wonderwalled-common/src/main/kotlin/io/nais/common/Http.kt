package io.nais.common

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.slf4j.event.Level
import java.util.UUID

fun defaultHttpClient() = HttpClient(CIO) {
    expectSuccess = true
    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
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

fun Application.commonSetup() {
    AutoConfiguredOpenTelemetrySdk.initialize()

    install(ContentNegotiation) {
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
}

fun ApplicationCall.requestHeaders(): Map<String, String> =
    request
        .headers
        .entries()
        .associate { header -> header.key to header.value.joinToString() }

fun ApplicationCall.bearerToken(): String? =
    request
        .parseAuthorizationHeader()
        ?.let { it as HttpAuthHeader.Single }
        ?.blob
