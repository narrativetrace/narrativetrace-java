/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.pipeline.PerishableMap;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Live event-stream bridge from NarrativeTrace events to OpenTelemetry spans.
 *
 * <p>INTENT: Plug this into the core pipeline when spans should be created as methods execute
 * rather than after the whole trace tree has been built.
 *
 * <p><b>@pattern</b> Maintains a {@link PerishableMap} of active spans keyed by NarrativeTrace span
 * id: enter starts a span, exit ends it. Orphaned spans (enter without matching exit) are
 * automatically evicted by the map's TTL and capacity limits, and ended with {@link
 * StatusCode#ERROR} and description "orphaned".
 *
 * <p><b>@llmNote</b> Parent-child relationships are reconstructed from explicit NarrativeTrace
 * parent span ids, not from OpenTelemetry's implicit thread-local context alone. Events may arrive
 * in any order due to concurrent execution — the span map handles interleaved enter/exit pairs from
 * independent traces correctly because each span is keyed by its unique {@link SpanId}.
 *
 * @see TraceSpanExporter
 */
public final class OtelTraceEventListener implements Consumer<TraceEvent> {

  private static final int DEFAULT_MAX_ACTIVE_SPANS = 1024;
  private static final Duration DEFAULT_TTL = Duration.ofHours(1);

  private final Tracer tracer;
  private final PerishableMap<SpanId, SpanFrame> activeSpans;

  /** Pairs an OTel span with the NarrativeTrace signature captured at enter time. */
  record SpanFrame(Span span, MethodSignature signature) {}

  /**
   * Creates a listener with default capacity (1024) and TTL (1 hour).
   *
   * @param tracer the OpenTelemetry tracer that receives one span per traced method
   */
  public OtelTraceEventListener(Tracer tracer) {
    this(tracer, DEFAULT_MAX_ACTIVE_SPANS, DEFAULT_TTL);
  }

  /**
   * Creates a listener with configurable capacity and TTL for orphan eviction.
   *
   * @param tracer the OTel tracer to create spans with
   * @param maxActiveSpans maximum number of active (unfinished) spans before oldest is evicted
   * @param ttl maximum age of an active span before it is evicted as orphaned
   */
  public OtelTraceEventListener(Tracer tracer, int maxActiveSpans, Duration ttl) {
    this.tracer = tracer;
    this.activeSpans =
        new PerishableMap<>(maxActiveSpans, ttl, frame -> endAsOrphaned(frame.span()));
  }

  @Override
  public void accept(TraceEvent event) {
    if (event instanceof TraceEvent.EnterEvent enter) {
      handleEnter(enter);
    } else if (event instanceof TraceEvent.ExitEvent exit) {
      handleExit(exit);
    }
  }

  private void handleEnter(TraceEvent.EnterEvent enter) {
    var sig = enter.signature();
    var sc = enter.spanContext();
    var builder = tracer.spanBuilder(sig.className() + "." + sig.methodName());
    builder.setStartTimestamp(enter.timestampNanos(), TimeUnit.NANOSECONDS);
    var parentFrame = sc.parentSpanId() != null ? activeSpans.get(sc.parentSpanId()) : null;
    if (parentFrame != null) {
      builder.setParent(Context.current().with(parentFrame.span()));
    }
    var span = builder.startSpan();
    SpanContextAttributeMapper.setSpanAttributes(sig, span);
    SpanContextAttributeMapper.setTraceIdentityAttributes(sc, span);
    SpanContextAttributeMapper.setNtSchemaAttributes(sc, span);
    if (sc.parentSpanId() == null) {
      SpanContextAttributeMapper.setTraceLevelAttributes(sc, span);
    }
    activeSpans.put(sc.spanId(), new SpanFrame(span, sig));
  }

  private static void endAsOrphaned(Span span) {
    span.setStatus(StatusCode.ERROR, "orphaned — exit event lost");
    span.end();
  }

  private void handleExit(TraceEvent.ExitEvent exit) {
    var frame = activeSpans.remove(exit.spanContext().spanId());
    if (frame == null) {
      handleExitWithoutEnter(exit);
      return;
    }
    SpanContextAttributeMapper.setOutcomeAttributes(exit.outcome(), frame.span());
    frame.span().end(exit.timestampNanos(), TimeUnit.NANOSECONDS);
    emitEventOnParent(exit, frame.signature());
  }

  private void emitEventOnParent(TraceEvent.ExitEvent exit, MethodSignature sig) {
    var parentSpanId = exit.spanContext().parentSpanId();
    if (parentSpanId == null) {
      return;
    }
    var parentFrame = activeSpans.get(parentSpanId);
    if (parentFrame == null) {
      return;
    }
    var attrs = SpanContextAttributeMapper.buildEventAttributes(sig, exit.outcome());
    var name = sig.className() + "." + sig.methodName();
    parentFrame.span().addEvent(name, attrs, exit.timestampNanos(), TimeUnit.NANOSECONDS);
  }

  private void handleExitWithoutEnter(TraceEvent.ExitEvent exit) {
    var sc = exit.spanContext();
    var span =
        tracer
            .spanBuilder(sc.spanId().toString())
            .setStartTimestamp(exit.timestampNanos(), TimeUnit.NANOSECONDS)
            .startSpan();
    SpanContextAttributeMapper.setTraceIdentityAttributes(sc, span);
    SpanContextAttributeMapper.setTraceLevelAttributes(sc, span);
    SpanContextAttributeMapper.setOutcomeAttributes(exit.outcome(), span);
    span.setStatus(StatusCode.ERROR, "orphaned — enter event lost");
    span.end(exit.timestampNanos(), TimeUnit.NANOSECONDS);
  }
}
