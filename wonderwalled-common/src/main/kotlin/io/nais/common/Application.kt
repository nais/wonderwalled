package io.nais.common

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.event.Level

fun Application.commonSetup() {
    installFeatures()

    routing {
        contextRoot()
        health()
    }
}

fun Application.installFeatures() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(IgnoreTrailingSlash)

    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        filter { call -> !call.request.path().startsWith("/internal") }
    }
}

fun Routing.health() {
    route("internal") {
        get("is_alive") {
            call.respond("alive")
        }
        get("is_ready") {
            call.respond("ready")
        }
    }
}

fun Routing.contextRoot() {
    get("/") {
        call.respondRedirect("/api/me")
    }
}
