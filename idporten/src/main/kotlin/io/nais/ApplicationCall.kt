package io.nais

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal

internal fun ApplicationCall.getTokenInfo(): Map<String, JsonNode>? = authentication
    .principal<JWTPrincipal>()
    ?.let { principal ->
        principal.payload.claims.entries.associate { claim ->
            claim.key to claim.value.`as`(JsonNode::class.java)
        }
    }

internal fun ApplicationCall.requestHeaders(): Map<String, String> = request.headers.entries()
    .associate { header -> header.key to header.value.joinToString() }
