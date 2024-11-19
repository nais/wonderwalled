package io.nais.common

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

fun openTelemetry(serviceName: String): OpenTelemetry {
    return AutoConfiguredOpenTelemetrySdk.builder().addResourceCustomizer { oldResource, _ ->
        oldResource.toBuilder()
            .putAll(oldResource.attributes)
            .put(ServiceAttributes.SERVICE_NAME, serviceName)
            .build()
    }.build().openTelemetrySdk
}

suspend fun <T> Tracer.withSpan(
    spanName: String,
    parameters: (SpanBuilder.() -> Unit)? = null,
    block: suspend (span: Span) -> T
): T {
    val span: Span = this.spanBuilder(spanName).run {
        if (parameters != null) parameters()
        startSpan()
    }

    return withContext(coroutineContext + span.asContextElement()) {
        try {
            block(span)
        } catch (throwable: Throwable) {
            span.setStatus(StatusCode.ERROR)
            span.recordException(throwable)
            throw throwable
        } finally {
            span.end()
        }
    }
}
