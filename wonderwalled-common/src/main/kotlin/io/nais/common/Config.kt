package io.nais.common

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

private val config =
    systemProperties() overriding
        EnvironmentVariables()

data class Config(
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val auth: Auth = Auth(config),
    // optional, generally only needed when running locally
    val ingress: String = config.getOrElse(key = Key("login.ingress", stringType), default = ""),
) {
    init {
        AutoConfiguredOpenTelemetrySdk.initialize()
    }

    data class Auth(
        val tokenEndpoint: String,
        val tokenExchangeEndpoint: String,
        val tokenIntrospectionEndpoint: String,
    ) {
        constructor(config: Configuration) : this(
            tokenEndpoint = config[Key("nais.token.endpoint", stringType)],
            tokenExchangeEndpoint = config[Key("nais.token.exchange.endpoint", stringType)],
            tokenIntrospectionEndpoint = config[Key("nais.token.introspection.endpoint", stringType)],
        )
    }
}
