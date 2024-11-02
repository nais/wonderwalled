package io.nais.common

import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

enum class IdentityProvider(val alias: String) {
    MASKINPORTEN("maskinporten"),
}

data class TexasConfiguration(
    val tokenEndpoint: String,
    val introspectionEndpoint: String,
) {
    constructor(config: Configuration) : this(
        tokenEndpoint = config[Key("texas.token.endpoint", stringType)],
        introspectionEndpoint = config[Key("texas.introspection.endpoint", stringType)],
    )
}

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
    private val config: TexasConfiguration,
    private val provider: IdentityProvider,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    suspend fun token(target: String) =
        httpClient
            .post(config.tokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasTokenRequest(target, provider.alias))
            }.body<TexasTokenResponse>()

    suspend fun introspect(accessToken: String) =
        httpClient
            .post(config.introspectionEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(TexasIntrospectionRequest(accessToken))
            }.body<Map<String, Any>>()
}
