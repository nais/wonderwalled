package io.nais.common

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.IgnoreTrailingSlash
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
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
