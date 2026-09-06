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
import ai.narrativetrace.api.event.TraceId
import ai.narrativetrace.api.event.Traceparent
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.simple.SimpleHttpResponseFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Cross-process trace continuity through the reactive filter. */
class TraceparentHttpFilterTest {
    private companion object {
        const val REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val REMOTE_SPAN_ID = "00f067aa0ba902b7"
        const val HEADER = "00-$REMOTE_TRACE_ID-$REMOTE_SPAN_ID-01"
    }

    private lateinit var context: ThreadLocalNarrativeContext
    private val exportedTrees = mutableListOf<TraceTree>()
    private lateinit var filter: NarrativeTraceHttpFilter

    private val exporter = TraceExporter { tree, _ -> exportedTrees.add(tree) }

    @BeforeEach
    fun setUp() {
        context = ThreadLocalNarrativeContext()
        filter = NarrativeTraceHttpFilter(context, exporter)
        exportedTrees.clear()
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
        context.reset()
    }

    @Test
    fun continuesTheCallersTraceWhenTheHeaderIsPresent() {
        runRequest(requestWith(Traceparent.HEADER_NAME to HEADER))

        val root = exportedTrees.single().roots()[0]
        assertThat(root.spanContext().traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID))
        assertThat(root.spanContext().parentSpanId()).isEqualTo(SpanId.of(REMOTE_SPAN_ID))
    }

    @Test
    fun matchesTheHeaderNameCaseInsensitively() {
        runRequest(requestWith("TraceParent" to HEADER))

        assertThat(
            exportedTrees
                .single()
                .roots()[0]
                .spanContext()
                .traceId(),
        ).isEqualTo(TraceId.of(REMOTE_TRACE_ID))
    }

    @Test
    fun startsAFreshTraceWhenNoHeaderArrives() {
        runRequest(requestWith())

        val root = exportedTrees.single().roots()[0]
        assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID))
        assertThat(root.spanContext().parentSpanId()).isNull()
    }

    @Test
    fun startsAFreshTraceAndServesTheRequestWhenTheHeaderIsMalformed() {
        var chainRan = false
        val chain =
            ServerFilterChain {
                chainRan = true
                traceOneMethod()
                Flux.just(okResponse())
            }
        Mono.from(filter.doFilter(requestWith(Traceparent.HEADER_NAME to "garbage"), chain)).block()

        assertThat(chainRan).isTrue()
        val root = exportedTrees.single().roots()[0]
        assertThat(root.spanContext().traceId()).isNotEqualTo(TraceId.of(REMOTE_TRACE_ID))
        assertThat(root.spanContext().parentSpanId()).isNull()
    }

    @Test
    fun publishesTheAdoptedTraceIdToMdc() {
        var mdcTraceId: String? = null
        val chain =
            ServerFilterChain {
                mdcTraceId = MDC.get("traceId")
                traceOneMethod()
                Flux.just(okResponse())
            }
        Mono.from(filter.doFilter(requestWith(Traceparent.HEADER_NAME to HEADER), chain)).block()

        assertThat(mdcTraceId).isEqualTo(REMOTE_TRACE_ID)
    }

    @Test
    fun offersAnOutboundHeaderNamingTheServicesOwnSpan() {
        var outbound: Traceparent? = null
        val chain =
            ServerFilterChain {
                val spanId = context.enterMethod(signature())
                outbound = context.outboundTraceparent()
                context.exitMethodWithReturn("\"ok\"", spanId)
                Flux.just(okResponse())
            }
        Mono.from(filter.doFilter(requestWith(Traceparent.HEADER_NAME to HEADER), chain)).block()

        assertThat(outbound!!.traceId()).isEqualTo(TraceId.of(REMOTE_TRACE_ID))
        assertThat(outbound!!.parentSpanId()).isNotEqualTo(SpanId.of(REMOTE_SPAN_ID))
    }

    private fun requestWith(vararg headers: Pair<String, String>): HttpRequest<*> {
        val request = HttpRequest.GET<Any>("/orders")
        headers.forEach { (name, value) -> request.header(name, value) }
        return request
    }

    private fun runRequest(request: HttpRequest<*>) {
        val chain =
            ServerFilterChain {
                traceOneMethod()
                Flux.just(okResponse())
            }
        Mono.from(filter.doFilter(request, chain)).block()
    }

    private fun traceOneMethod() {
        val spanId = context.enterMethod(signature())
        context.exitMethodWithReturn("\"ok\"", spanId)
    }

    private fun signature() = MethodSignature("OrderService", "list", emptyList(), null, null)

    private fun okResponse(): MutableHttpResponse<*> = SimpleHttpResponseFactory.INSTANCE.status<Any>(HttpStatus.OK)
}
