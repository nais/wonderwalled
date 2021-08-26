package io.nais

import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking

private val config = systemProperties() overriding
    EnvironmentVariables() overriding
    ConfigurationProperties.fromResource("application.properties")

data class Configuration(
    val application: Application = Application(),
    val openid: OpenId = OpenId()
) {
    data class Application(
        val port: Int = config[Key("application.port", intType)],
        val name: String = config[Key("application.name", stringType)],
        val secure: Boolean = config[Key("application.secure", booleanType)]
    )

    data class OpenId(
        val clientId: String = config[Key("idporten.client.id", stringType)],
        val wellKnownConfigurationUrl: String = config[Key("idporten.well.known.url", stringType)],
        val openIdConfiguration: OpenIdConfiguration = runBlocking {
            httpClient.get(wellKnownConfigurationUrl)
        }
    )
}

data class OpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String
)
