package io.nais

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.nais.common.AccessToken
import io.nais.common.defaultHttpClient

class AzureAdClient(
    private val config: Configuration.Azure,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private suspend inline fun fetchAccessToken(formParameters: Parameters): AccessToken =
        httpClient.submitForm(
            url = config.openIdConfiguration.tokenEndpoint,
            formParameters = formParameters,
        ).body()

    // Service-to-service access token request (client credentials grant)
    suspend fun getMachineToMachineAccessToken(audience: String): AccessToken =
        fetchAccessToken(
            Parameters.build {
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("scope", audience.toScope())
                append("grant_type", "client_credentials")
            },
        )

    // Service-to-service access token request (on-behalf-of flow)
    suspend fun getOnBehalfOfAccessToken(
        audience: String,
        accessToken: String,
    ): AccessToken =
        fetchAccessToken(
            Parameters.build {
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("scope", audience.toScope())
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("requested_token_use", "on_behalf_of")
                append("assertion", accessToken)
                append("assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            },
        )
}

private fun String.toScope(): String = "api://${this.replace(":", ".")}/.default"
