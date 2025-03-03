package io.nais.common

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

suspend fun <T> Tracer.withSpan(
    spanName: String,
    attributes: Attributes? = null,
    block: suspend (span: Span) -> T,
): T {
    val span: Span =
        this.spanBuilder(spanName).run {
            if (attributes != null) {
                setAllAttributes(attributes)
            }
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
