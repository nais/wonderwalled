package io.nais.common

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.auth.principal
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader

fun ApplicationCall.getTokenInfo(): Map<String, JsonNode>? = authentication
    .principal<JWTPrincipal>()
    ?.let { principal ->
        principal.payload.claims.entries.associate { claim ->
            claim.key to claim.value.`as`(JsonNode::class.java)
        }
    }

fun ApplicationCall.requestHeaders(): Map<String, String> = request.headers.entries()
    .associate { header -> header.key to header.value.joinToString() }

fun ApplicationCall.bearerToken(): String? = request.parseAuthorizationHeader()?.let {
    it as HttpAuthHeader.Single
}?.blob
