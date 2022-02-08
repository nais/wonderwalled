package io.nais.common

import com.fasterxml.jackson.annotation.JsonProperty

const val WELL_KNOWN_PATH = "/.well-known"
const val OPENID_CONFIGURATION_PATH = "/openid-configuration"
const val WELL_KNOWN_OPENID_CONFIGURATION_PATH = WELL_KNOWN_PATH + OPENID_CONFIGURATION_PATH

data class OpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String
) {
    fun validate(wellKnownConfigurationUrl: String) {
        val resolvedAuthority: String = this.issuer.removeSuffix("/") + WELL_KNOWN_OPENID_CONFIGURATION_PATH
        if (resolvedAuthority != wellKnownConfigurationUrl) {
            throw invalidOpenIdConfigurationException(wellKnownConfigurationUrl, resolvedAuthority)
        }
    }
}

private fun invalidOpenIdConfigurationException(expected: String, got: String): RuntimeException {
    return RuntimeException("authority does not match the issuer returned by provider: expected $expected, got $got")
}
