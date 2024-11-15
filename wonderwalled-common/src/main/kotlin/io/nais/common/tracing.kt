package io.nais.common

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes

fun openTelemetry(serviceName: String): OpenTelemetry {
    return AutoConfiguredOpenTelemetrySdk.builder().addResourceCustomizer { oldResource, _ ->
        oldResource.toBuilder()
            .putAll(oldResource.attributes)
            .put(ServiceAttributes.SERVICE_NAME, serviceName)
            .build()
    }.build().openTelemetrySdk
}

suspend fun <T> Tracer?.withSpan(
    spanName: String,
    attributes: Map<AttributeKey<String>, String> = emptyMap(),
    block: suspend () -> T
): T {
    val span = this?.spanBuilder(spanName)?.startSpan()?.apply {
        attributes.forEach { (key, value) -> this.setAttribute(key, value) }
    }

    if (this == null || span == null) {
        return block()
    }

    return try {
        span.makeCurrent().use { _ ->
            block()
        }
    } finally {
        span.end()
    }
}
