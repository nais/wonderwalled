package io.nais.common

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

const val WELL_KNOWN_PATH = "/.well-known"
const val OPENID_CONFIGURATION_PATH = "/openid-configuration"
const val OAUTH_AUTHORIZATION_SERVER_PATH = "/oauth-authorization-server"
const val WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER_PATH = WELL_KNOWN_PATH + OAUTH_AUTHORIZATION_SERVER_PATH
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
        val resolvedOpenIDAuthority: String = this.issuer.removeSuffix("/") + WELL_KNOWN_OPENID_CONFIGURATION_PATH
        val resolvedOAuthAuthority: String = this.issuer.removeSuffix("/") + WELL_KNOWN_OAUTH_AUTHORIZATION_SERVER_PATH

        if (resolvedOpenIDAuthority != wellKnownConfigurationUrl && resolvedOAuthAuthority != wellKnownConfigurationUrl) {
            throw invalidOpenIdConfigurationException(
                expected = listOf(resolvedOpenIDAuthority, resolvedOAuthAuthority),
                got = wellKnownConfigurationUrl
            )
        }
    }
}

private fun invalidOpenIdConfigurationException(expected: List<String>, got: String): RuntimeException {
    return RuntimeException("authority does not match the issuer returned by provider: got $got, expected one of $expected")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Int,
    @JsonProperty("token_type")
    val tokenType: String
)
