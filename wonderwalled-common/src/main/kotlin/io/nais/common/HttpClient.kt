package io.nais.common

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.client.KtorClientTracing
import kotlinx.coroutines.runBlocking

fun defaultHttpClient(
    openTelemetry: OpenTelemetry? = null,
) = HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) {
        jackson {
            deserializationConfig.apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    openTelemetry?.let {
        install(KtorClientTracing) {
            setOpenTelemetry(it)
            attributeExtractor {
                onStart {
                    attributes.put("start-time", System.currentTimeMillis())
                }
                onEnd {
                    attributes.put("end-time", System.currentTimeMillis())
                }
            }
        }
    }
}

fun HttpClient.getOpenIdConfiguration(url: String): OpenIdConfiguration =
    runBlocking {
        get(url)
            .body<OpenIdConfiguration>()
            .also { it.validate(url) }
    }
