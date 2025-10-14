package io.nais

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
    private fun azure(block: suspend ApplicationTestBuilder.() -> Unit) =
        testWonderwalled(Application::azure) {
            block()
        }

    @Test
    fun `GET api me with valid token returns claims`() =
        azure {
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
        azure {
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
        azure {
            client = createClient { followRedirects = false }

            val response = client.get("/api/me")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("https://wonderwall.local/oauth2/login?redirect=/api/me", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `GET api obo success returns token response`() =
        azure {
            mockExternalServices(exchangeCallback = { params ->
                assertTrue(params.containsKey("target"))
                // assert that transformation doesn't happen
                assertEquals("api://cluster.namespace.api/.default", params["target"]?.first())
            })
            val response = client.authGet("/api/obo?aud=api://cluster.namespace.api/.default")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_EXCHANGED_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }

    @Test
    fun `GET api obo error returns error response`() =
        azure {
            mockExternalServices(
                exchangeStatus = HttpStatusCode.BadRequest,
                exchangeResponse =
                    """
                    {
                        "error": "invalid_scope",
                        "error_description": "the requested scope is invalid"
                    }
                    """.trimIndent(),
            )

            val response = client.authGet("/api/obo?aud=api://cluster.namespace.bad-api/.default")
            assertEquals(HttpStatusCode.BadRequest, response.status)

            val body = response.body<TokenErrorResponse>()
            assertEquals("invalid_scope", body.error)
            assertEquals("the requested scope is invalid", body.errorDescription)
        }

    @Test
    fun `GET api obo missing aud returns 400`() =
        azure {
            val response = client.authGet("/api/obo")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("missing 'aud'"))
        }

    @Test
    fun `GET api obo audience transformation happens`() =
        azure {
            mockExternalServices(exchangeCallback = { params ->
                assertTrue(params.containsKey("target"))
                assertEquals("api://cluster.namespace.api/.default", params["target"]?.first())
            })

            val response = client.authGet("/api/obo?aud=cluster:namespace:api")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_EXCHANGED_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }

    @Test
    fun `GET api m2m success returns token response`() =
        azure {
            mockExternalServices(tokenCallback = { params ->
                assertTrue(params.containsKey("target"))
                // assert that transformation doesn't happen
                assertEquals("api://cluster.namespace.api/.default", params["target"]?.first())
            })
            val response = client.authGet("/api/m2m?aud=api://cluster.namespace.api/.default")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }

    @Test
    fun `GET api m2m error returns error response`() =
        azure {
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

            val response = client.authGet("/api/m2m?aud=api://cluster.namespace.bad-api/.default")
            assertEquals(HttpStatusCode.BadRequest, response.status)

            val body = response.body<TokenErrorResponse>()
            assertEquals("invalid_scope", body.error)
            assertEquals("the requested scope is invalid", body.errorDescription)
        }

    @Test
    fun `GET api m2m missing aud returns 400`() =
        azure {
            val response = client.authGet("/api/m2m")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `GET api m2m audience transformation happens`() =
        azure {
            mockExternalServices(tokenCallback = { params ->
                assertTrue(params.containsKey("target"))
                assertEquals("api://cluster.namespace.api/.default", params["target"]?.first())
            })

            val response = client.authGet("/api/m2m?aud=cluster:namespace:api")
            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<TokenResponse.Success>()
            assertEquals("SOME_TOKEN", body.accessToken)
            assertEquals(3600, body.expiresInSeconds)
        }
}
