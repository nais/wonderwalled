package io.nais.common

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.parseAuthorizationHeader

fun ApplicationCall.getTokenInfo(): Map<String, JsonNode>? = authentication
    .principal<JWTPrincipal>()?.let { principal ->
        principal.payload.claims.entries.associate { claim ->
            claim.key to claim.value.`as`(JsonNode::class.java)
        }
    }

fun ApplicationCall.requestHeaders(): Map<String, String> = request.headers.entries()
    .associate { header -> header.key to header.value.joinToString() }

fun ApplicationCall.bearerToken(): String? = request
    .parseAuthorizationHeader()
    ?.let { it as HttpAuthHeader.Single }
    ?.blob
