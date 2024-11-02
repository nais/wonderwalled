package io.nais

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.nais.common.OpenIdConfiguration
import io.nais.common.TexasConfiguration
import io.nais.common.defaultHttpClient
import io.nais.common.getOpenIdConfiguration

private val config =
    systemProperties() overriding
        EnvironmentVariables()

data class Configuration(
    val port: Int = config.getOrElse(Key("application.port", intType), 8080),
    val azure: Azure = Azure(),
    val texas: TexasConfiguration = TexasConfiguration(config),
    // optional, generally only needed when running locally
    val ingress: String =
        config.getOrElse(
            key = Key("wonderwall.ingress", stringType),
            default = "",
        ),
) {
    data class Azure(
        val clientId: String = config[Key("azure.app.client.id", stringType)],
        val wellKnownConfigurationUrl: String = config[Key("azure.app.well.known.url", stringType)],
        val openIdConfiguration: OpenIdConfiguration =
            defaultHttpClient().getOpenIdConfiguration(wellKnownConfigurationUrl),
    )
}
