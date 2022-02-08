package io.nais

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.nais.common.OpenIdConfiguration
import io.nais.common.defaultHttpClient
import io.nais.common.getOpenIdConfiguration

private val config = systemProperties() overriding
    EnvironmentVariables()

data class Configuration(
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val idporten: IdPorten = IdPorten(),

    // optional, generally only needed when running locally
    val ingress: String = config.getOrElse(
        key = Key("wonderwall.ingress", stringType),
        default = ""
    ),
) {
    data class IdPorten(
        val clientId: String = config[Key("idporten.client.id", stringType)],
        val wellKnownConfigurationUrl: String = config[Key("idporten.well.known.url", stringType)],
        val openIdConfiguration: OpenIdConfiguration = defaultHttpClient().getOpenIdConfiguration(wellKnownConfigurationUrl)
    )
}
