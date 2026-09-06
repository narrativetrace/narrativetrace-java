/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.event.MethodSignature
import ai.narrativetrace.api.event.SpanId
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import ai.narrativetrace.core.context.ContextSnapshot
import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.simple.SimpleHttpResponseFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

/**
 * A request Micronaut handled is never turned into a different terminal signal by tracing.
 *
 * INTENT: The 2026-09-01 bug hunt found `exportTrace` running its capture outside any guard and
 * `doFinally` calling `MDC.clear()` and `context.reset()` bare. `doFinally` is the worst place for a
 * throw in a reactive chain: it replaces the signal the application produced, success or error.
 */
class HttpFilterTotalityTest {
    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    /** Everything the filter asks of a context, forwarded to a real one. */
    private open class DelegatingContext : NarrativeContext {
        val delegate = ThreadLocalNarrativeContext()

        override fun enterMethod(signature: MethodSignature): SpanId = delegate.enterMethod(signature)

        override fun detachFrame(spanId: SpanId?) = delegate.detachFrame(spanId)

        override fun exitMethodWithReturn(renderedReturnValue: String?) = delegate.exitMethodWithReturn(renderedReturnValue)

        override fun exitMethodWithReturn(
            renderedReturnValue: String?,
            spanId: SpanId?,
        ) = delegate.exitMethodWithReturn(renderedReturnValue, spanId)

        override fun exitMethodWithException(
            exception: Throwable?,
            errorContext: String?,
        ) = delegate.exitMethodWithException(exception, errorContext)

        override fun exitMethodWithException(
            exception: Throwable?,
            errorContext: String?,
            spanId: SpanId?,
        ) = delegate.exitMethodWithException(exception, errorContext, spanId)

        override fun captureTrace(): TraceTree = delegate.captureTrace()

        override fun reset() = delegate.reset()

        override fun snapshot(): ContextSnapshot = delegate.snapshot()
    }

    private class ResetFailingContext : DelegatingContext() {
        val resets = AtomicInteger()

        override fun reset() {
            resets.incrementAndGet()
            throw AssertionError("reset must not fail the request")
        }
    }

    private class CaptureFailingContext : DelegatingContext() {
        override fun captureTrace(): TraceTree = throw AssertionError("captureTrace must not fail the request")
    }

    private val explodingExporter = TraceExporter { _, _ -> throw AssertionError("exporter must not fail the request") }

    @Test
    fun anExporterThrowingAnErrorLeavesTheResponseIntact() {
        val context = ThreadLocalNarrativeContext()
        val filter = NarrativeTraceHttpFilter(context, explodingExporter)

        val result = Mono.from(filter.doFilter(getRequest("/test"), tracingChain(context))).block()

        assertThat(result).isNotNull()
    }

    @Test
    fun aCaptureThatThrowsLeavesTheResponseIntact() {
        val filter = NarrativeTraceHttpFilter(CaptureFailingContext(), TraceExporter { _, _ -> })

        val result = Mono.from(filter.doFilter(getRequest("/test"), chain { okResponse() })).block()

        assertThat(result).isNotNull()
    }

    @Test
    fun aContextResetThatThrowsInDoFinallyDoesNotAlterTheTerminalSignal() {
        val context = ResetFailingContext()
        val filter = NarrativeTraceHttpFilter(context, TraceExporter { _, _ -> })

        val result = Mono.from(filter.doFilter(getRequest("/test"), chain { okResponse() })).block()

        assertThat(result).isNotNull()
        assertThat(context.resets.get()).describedAs("reset before the request and after it").isEqualTo(2)
    }

    @Test
    fun aFailingChainKeepsItsOwnErrorWhenExportAndCleanupAlsoFail() {
        val filter = NarrativeTraceHttpFilter(ResetFailingContext(), explodingExporter)

        assertThatThrownBy {
            Mono.from(filter.doFilter(getRequest("/test"), errorChain(IllegalStateException("the application failed")))).block()
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("the application failed")
    }

    private fun getRequest(path: String): HttpRequest<*> = HttpRequest.GET<Any>(path)

    private fun okResponse(): MutableHttpResponse<*> = SimpleHttpResponseFactory.INSTANCE.status<Any>(HttpStatus.OK)

    private fun chain(block: () -> MutableHttpResponse<*>): ServerFilterChain = ServerFilterChain { Flux.just(block()) }

    private fun tracingChain(context: ThreadLocalNarrativeContext): ServerFilterChain =
        ServerFilterChain {
            context.enterMethod(MethodSignature("Handler", "handle", listOf()))
            context.exitMethodWithReturn("\"ok\"")
            Flux.just(okResponse())
        }

    private fun errorChain(error: Throwable): ServerFilterChain = ServerFilterChain { Flux.error(error) }
}
