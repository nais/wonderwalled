package io.nais

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.nais.common.TokenErrorResponse
import io.nais.common.TokenResponse
import io.nais.common.authGet
import io.nais.common.mockExternalServices
import io.nais.common.testWonderwalled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WonderwalledIntegrationTest {
    private fun maskinporten(block: suspend ApplicationTestBuilder.() -> Unit) =
        testWonderwalled(Application::maskinporten) {
            block()
        }

    @Test
    fun `GET api me with valid token returns claims`() =
        maskinporten {
            mockExternalServices(
                introspectResponse =
                    """
                    {
                        "active": true,
                        "sub": "user123",
                        "preferred_username": "user@example.com",
                        "some_other_claim": "some_value"
                    }
                    """.trimIndent(),
            )

            val response = client.authGet("/api/me")
            assertEquals(HttpStatusCode.OK, response.status)

            data class Claims(
                val sub: String,
                @get:JsonProperty("preferred_username") val preferredUsername: String,
                @get:JsonProperty("some_other_claim") val someOtherClaim: String,
            )
            val body = response.body<Claims>()
            assertEquals("user123", body.sub)
            assertEquals("user@example.com", body.preferredUsername)
            assertEquals("some_value", body.someOtherClaim)
        }

    @Test
    fun `GET api me with invalid token results in redirect`() =
        maskinporten {
            mockExternalServices(
                introspectResponse =
                    """
                    {
                        "active": false,
                        "error": "invalid_token"
                    }
                    """.trimIndent(),
            )
            client = createClient { followRedirects = false }

            val response = client.authGet("/api/me")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("https://wonderwall.local/oauth2/login?redirect=/api/me", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `GET api me without token results in redirect`() =
        maskinporten {
            client = createClient { followRedirects = false }

            val response = client.get("/api/me")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("https://wonderwall.local/oauth2/login?redirect=/api/me", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `GET api token success returns token response`() =
        maskinporten {
            mockExternalServices(
                tokenCallback = { params ->
                    assertTrue(params.containsKey("target"))
                    assertEquals("nav:test/api", params["target"]?.first())
                },
            )
            val response = client.authGet("/api/token")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }

    @Test
    fun `GET api token with scope parameter returns success`() =
        maskinporten {
            mockExternalServices(
                tokenCallback = { params ->
                    assertTrue(params.containsKey("target"))
                    assertEquals("some-scope", params["target"]?.first())
                },
            )
            val response = client.authGet("/api/token?scope=some-scope")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }

    @Test
    fun `GET api token error returns error response`() =
        maskinporten {
            mockExternalServices(
                tokenStatus = HttpStatusCode.BadRequest,
                tokenResponse =
                    """
                    {
                        "error": "invalid_scope",
                        "error_description": "the requested scope is invalid"
                    }
                    """.trimIndent(),
            )

            val response = client.authGet("/api/token?scope=invalid-scope")
            assertEquals(HttpStatusCode.BadRequest, response.status)

            val body = response.body<TokenErrorResponse>()
            assertEquals("invalid_scope", body.error)
            assertEquals("the requested scope is invalid", body.errorDescription)
        }

    @Test
    fun `GET api introspect success returns claims`() =
        maskinporten {
            mockExternalServices(
                introspectResponse =
                    """
                    {
                        "active": true,
                        "sub": "authenticated-principal",
                        "scope": "some-scope",
                        "client_id": "some-client-id"
                    }
                    """.trimIndent(),
            )
            val response = client.authGet("/api/introspect")
            assertEquals(HttpStatusCode.OK, response.status)

            data class Claims(
                val active: Boolean,
                val sub: String,
                val scope: String,
                @get:JsonProperty("client_id") val clientId: String,
            )
            val body = response.body<Claims>()
            assertEquals(true, body.active)
            assertEquals("authenticated-principal", body.sub)
            assertEquals("some-scope", body.scope)
            assertEquals("some-client-id", body.clientId)
        }

    @Test
    fun `GET api introspect with scope parameter returns success`() =
        maskinporten {
            mockExternalServices(
                tokenCallback = { params ->
                    assertTrue(params.containsKey("target"))
                    assertEquals("some-scope", params["target"]?.first())
                },
                introspectResponse =
                    """
                    {
                        "active": true,
                        "sub": "authenticated-principal",
                        "scope": "some-scope",
                        "client_id": "some-client-id"
                    }
                    """.trimIndent(),
            )

            val response = client.authGet("/api/introspect?scope=some-scope")
            assertEquals(HttpStatusCode.OK, response.status)

            data class Claims(
                val active: Boolean,
                val sub: String,
                val scope: String,
                @get:JsonProperty("client_id") val clientId: String,
            )
            val body = response.body<Claims>()
            assertEquals(true, body.active)
            assertEquals("authenticated-principal", body.sub)
            assertEquals("some-scope", body.scope)
            assertEquals("some-client-id", body.clientId)
        }
}
