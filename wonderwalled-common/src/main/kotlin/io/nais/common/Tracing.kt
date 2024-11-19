package io.nais.common

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
