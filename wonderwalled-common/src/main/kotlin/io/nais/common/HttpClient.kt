package io.nais.common

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking

fun defaultHttpClient() =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                deserializationConfig.apply {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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
