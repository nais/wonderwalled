package io.nais.common

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private val config =
    systemProperties() overriding
        EnvironmentVariables()

data class AppConfig(
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val auth: AuthClientConfig = AuthClientConfig(config),
    // optional, generally only needed when running locally
    val ingress: String = config.getOrElse(key = Key("login.ingress", stringType), default = ""),
)
