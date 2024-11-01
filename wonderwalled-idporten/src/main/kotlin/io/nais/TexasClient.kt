package io.nais

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.nais.common.defaultHttpClient

const val PROVIDER_MASKINPORTEN = "maskinporten"

data class TexasTokenRequest(
    val target: String,
    @JsonProperty("identity_provider")
    val identityProvider: String,
)

data class TexasTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
)

data class TexasIntrospectionRequest(
    val token: String,
)

class TexasClient(
    private val config: Configuration.Texas,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    suspend fun token(target: String) =
        httpClient
            .post(config.texasTokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasTokenRequest(target, PROVIDER_MASKINPORTEN))
            }.body<TexasTokenResponse>()

    suspend fun introspect(accessToken: String) =
        httpClient
            .post(config.texasIntrospectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasIntrospectionRequest(accessToken))
            }.body<String>()
}
