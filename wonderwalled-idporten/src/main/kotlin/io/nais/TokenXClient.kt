package io.nais

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.nais.common.AccessToken
import io.nais.common.defaultHttpClient
import java.time.Instant
import java.util.Date
import java.util.UUID

class TokenXClient(
    private val config: Configuration.TokenX,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private suspend inline fun fetchAccessToken(formParameters: Parameters): AccessToken =
        httpClient
            .submitForm(
                url = config.openIdConfiguration.tokenEndpoint,
                formParameters = formParameters,
            ).body()

    private fun clientAssertion(): String {
        val now = Date.from(Instant.now())
        return JWTClaimsSet
            .Builder()
            .issuer(config.clientId)
            .subject(config.clientId)
            .audience(config.openIdConfiguration.tokenEndpoint)
            .issueTime(now)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .notBeforeTime(now)
            .build()
            .sign()
            .serialize()
    }

    private fun JWTClaimsSet.sign(): SignedJWT =
        SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(config.rsaKey.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            this,
        ).apply {
            sign(RSASSASigner(config.rsaKey.toPrivateKey()))
        }

    suspend fun getOnBehalfOfAccessToken(
        audience: String,
        accessToken: String,
    ): AccessToken =
        fetchAccessToken(
            Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                append("client_assertion", clientAssertion())
                append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                append("subject_token", accessToken)
                append("audience", audience)
            },
        )
}
