package io.nais

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.IgnoreTrailingSlash
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.event.Level

internal val httpClient = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

fun main() {
    val config = Configuration()

    embeddedServer(Netty, port = config.application.port) {
        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
        install(IgnoreTrailingSlash)
        install(CallLogging) {
            level = Level.INFO
        }
        jwtAuthentication(config)

        routing {
            internal()
            authenticate {
                api()
            }
        }
    }.start(wait = true)
}
