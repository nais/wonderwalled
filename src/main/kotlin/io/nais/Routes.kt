package io.nais

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route

internal fun Route.internal() {
    route("internal") {
        get("is_alive") {
            call.respond("alive")
        }
        get("is_ready") {
            call.respond("ready")
        }
    }
}

internal fun Route.api() {
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
    }
}
