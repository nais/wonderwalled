package io.nais.common
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.toMap

fun TestApplicationBuilder.mockExternalServices(
    tokenResponse: String =
        """
        {
            "access_token": "SOME_TOKEN",
            "expires_in": 3600
        }
        """.trimIndent(),
    tokenStatus: HttpStatusCode = HttpStatusCode.OK,
    tokenCallback: ((parameters: Map<String, List<String>>) -> Unit)? = null,
    exchangeResponse: String =
        """
        {
            "access_token": "SOME_EXCHANGED_TOKEN",
            "expires_in": 3600
        }
        """.trimIndent(),
    exchangeStatus: HttpStatusCode = HttpStatusCode.OK,
    exchangeCallback: ((parameters: Map<String, List<String>>) -> Unit)? = null,
    introspectResponse: String =
        """
        {
            "active": true,
            "sub": "authenticated-user"
        }
        """.trimIndent(),
    fakedingsCallback: ((parameters: Map<String, List<String>>) -> Unit)? = null,
) {
    externalServices {
        hosts("https://texas.local") {
            routing {
                post("/token") {
                    tokenCallback?.invoke(call.receiveParameters().toMap())
                    call.respondText(tokenResponse, ContentType.Application.Json, status = tokenStatus)
                }
                post("/token/exchange") {
                    exchangeCallback?.invoke(call.receiveParameters().toMap())
                    call.respondText(exchangeResponse, ContentType.Application.Json, status = exchangeStatus)
                }
                post("/token/introspect") {
                    call.respondText(introspectResponse, ContentType.Application.Json)
                }
            }
        }
        hosts("https://fakedings.local") {
            routing {
                get("/fake/idporten") {
                    fakedingsCallback?.invoke(call.request.queryParameters.toMap())
                    call.respondText("FAKE_IDPORTEN_TOKEN")
                }
            }
        }
    }
}

suspend fun HttpClient.authGet(path: String) =
    get(path) {
        header(HttpHeaders.Authorization, "Bearer SOME_TOKEN")
    }

fun testWonderwalled(
    module: Application.(Config, HttpClient) -> Unit,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    mockExternalServices()

    val testConfig =
        Config(
            port = 0,
            auth =
                Config.Auth(
                    tokenEndpoint = "https://texas.local/token",
                    tokenExchangeEndpoint = "https://texas.local/token/exchange",
                    tokenIntrospectionEndpoint = "https://texas.local/token/introspect",
                ),
            ingress = "https://wonderwall.local",
            fakedings = Config.Fakedings(url = "https://fakedings.local/fake"),
        )

    application {
        // pass in http client from testApplication to ensure that externalServices mocking works.
        module(
            testConfig,
            this@testApplication.createClient {
                expectSuccess = true
                install(ContentNegotiation) { jackson() }
            },
        )
    }

    client =
        createClient {
            install(ContentNegotiation) { jackson() }
        }

    block()
}
