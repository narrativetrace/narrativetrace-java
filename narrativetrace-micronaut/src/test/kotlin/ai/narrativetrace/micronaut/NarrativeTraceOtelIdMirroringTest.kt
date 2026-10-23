/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.api.event.MethodSignature
import ai.narrativetrace.api.event.SpanContext
import ai.narrativetrace.api.event.SpanIdGenerator
import ai.narrativetrace.api.event.TraceEvent
import ai.narrativetrace.api.event.TraceOutcome
import ai.narrativetrace.opentelemetry.OtelTraceEventListener
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** B-161: id mirroring is on by default in a Micronaut app with OpenTelemetry tracing, opt-out. */
class NarrativeTraceOtelIdMirroringTest {
    @Test
    fun micronautsOpenTelemetryMirrorsNarrativeTraceIdsByDefault() {
        ApplicationContext.run(mapOf<String, Any>(RECORDER to "true")).use { ctx ->
            assertThat(ctx.containsBean(NarrativeTraceOtelIdMirroring::class.java)).isTrue()
            assertThat(spanIdIsMirrored(ctx)).isTrue()
        }
    }

    @Test
    fun theMicronautPropertyTurnsMirroringOff() {
        val props = mapOf<String, Any>(RECORDER to "true", "narrativetrace.otel.mirror-ids" to "false")
        ApplicationContext.run(props).use { ctx ->
            assertThat(spanIdIsMirrored(ctx))
                .`as`("off here must also keep the autoconfigure service entry from installing it")
                .isFalse()
        }
    }

    private fun spanIdIsMirrored(ctx: ApplicationContext): Boolean {
        val otel = ctx.getBean(OpenTelemetry::class.java)
        val recorder = ctx.getBean(StartedSpans::class.java)
        val listener = OtelTraceEventListener(otel.getTracer("t"))
        val sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build()
        val sig = MethodSignature("Service", "work", listOf())
        listener.accept(TraceEvent.EnterEvent(sc, 1_000, sig))
        listener.accept(TraceEvent.ExitEvent(sc, 2_000, TraceOutcome.Returned("1"), null))
        return recorder.spanIds.single() == sc.spanId().toString()
    }

    companion object {
        const val RECORDER = "narrativetrace-test.span-recorder"
    }
}

/** Records the id of every span the SDK starts; the factory picks up a `SpanProcessor` bean. */
@Singleton
@Requires(property = NarrativeTraceOtelIdMirroringTest.RECORDER, value = "true")
class StartedSpans : SpanProcessor {
    val spanIds = CopyOnWriteArrayList<String>()

    override fun onStart(
        parentContext: Context,
        span: ReadWriteSpan,
    ) {
        spanIds.add(span.spanContext.spanId)
    }

    override fun isStartRequired() = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired() = false
}
