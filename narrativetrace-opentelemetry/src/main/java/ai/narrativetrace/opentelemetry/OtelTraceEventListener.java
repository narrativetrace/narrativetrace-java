/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.pipeline.PerishableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
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
 * independent traces correctly because each span is keyed by its unique {@link SpanId}. A parent id
 * that names no active frame is not necessarily rootless: under an adopted {@code traceparent} it
 * names the remote caller's span, so the started span parents on an OTel remote {@code SpanContext}
 * built from the header's trace id, parent span id and flags — never a synthetic active span.
 *
 * <p><b>@edgeCase</b> A child can start after its local parent has ended — work resumed from a
 * {@code snapshot()} by a scheduler, a fire-and-forget member. Ended frames are remembered in a
 * second bounded map (same capacity and TTL as the active one), so such a child parents on its real
 * OTel parent. A child later than that memory (past the TTL after its parent's end, or past {@code
 * maxActiveSpans} newer finished frames) gets a parent context reconstructed from the
 * NarrativeTrace parent id: a local context (never presented as a remote, backend-known one) and,
 * unless ids are mirrored, marked on the child with {@code narrative.parent.synthetic=true} and
 * {@code narrative.parent_span_id}. The two unknown-parent cases are told apart by the event: a
 * span opened by local hand-off (snapshot, fork, fire-and-forget) carries {@code ConcurrencyInfo};
 * a root under an adopted {@code traceparent} does not.
 *
 * <p><b>@edgeCase</b> Mode is detected at runtime from the first span started: when its OTel span
 * id equals its NarrativeTrace span id, the SDK mirrors ids ({@link NarrativeTraceIdGenerator}),
 * the reconstructed parent is exact, and finished frames need no memory. Otherwise (no generator
 * installed, the OpenTelemetry Java agent's isolated SDK, the API's no-op tracer) the memory and
 * the synthetic marking apply.
 *
 * @see TraceSpanExporter
 */
public final class OtelTraceEventListener implements Consumer<TraceEvent> {

  private static final int DEFAULT_MAX_ACTIVE_SPANS = 1024;
  private static final Duration DEFAULT_TTL = Duration.ofHours(1);

  private final Tracer tracer;
  static final AttributeKey<Boolean> PARENT_SYNTHETIC =
      AttributeKey.booleanKey("narrative.parent.synthetic");
  static final AttributeKey<String> PARENT_SPAN_ID =
      AttributeKey.stringKey("narrative.parent_span_id");

  private final PerishableMap<SpanId, SpanFrame> activeSpans;

  /**
   * Parents a span may still name although they are not active: ended local frames (not-mirrored
   * mode only) and remote parents adopted from a {@code traceparent}. Bounded like {@code
   * activeSpans}; an entry ages out silently.
   */
  private final PerishableMap<SpanId, io.opentelemetry.api.trace.SpanContext> knownParents;

  /** {@code null} until the first span starts; then whether the SDK mirrors NarrativeTrace ids. */
  private volatile Boolean mirroredIds;

  private final MonotonicToEpoch clock = new MonotonicToEpoch();

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
    this.knownParents = new PerishableMap<>(maxActiveSpans, ttl, forgotten -> {});
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
    builder.setStartTimestamp(clock.epochNanos(enter.timestampNanos()), TimeUnit.NANOSECONDS);
    boolean synthetic = applyParent(builder, enter);
    var span = startMirrored(builder, sc);
    if (synthetic && !Boolean.TRUE.equals(mirroredIds)) {
      span.setAttribute(PARENT_SYNTHETIC, true);
      span.setAttribute(PARENT_SPAN_ID, sc.parentSpanId().toString());
    }
    SpanContextAttributeMapper.setSpanAttributes(sig, span);
    SpanContextAttributeMapper.setTraceIdentityAttributes(sc, span);
    SpanContextAttributeMapper.setNtSchemaAttributes(sc, span);
    if (sc.parentSpanId() == null) {
      SpanContextAttributeMapper.setTraceLevelAttributes(sc, span);
    }
    activeSpans.put(sc.spanId(), new SpanFrame(span, sig));
  }

  /**
   * Parents the started span: on an active local frame; else on a remembered one (an ended local
   * frame, or an adopted remote parent); else — the id is known to nobody here — on the remote
   * context an adopted {@code traceparent} named, or, for a span opened by local hand-off, on a
   * local context reconstructed from the NarrativeTrace parent id.
   *
   * @return whether the parent was reconstructed
   */
  private boolean applyParent(SpanBuilder builder, TraceEvent.EnterEvent enter) {
    var sc = enter.spanContext();
    if (sc.parentSpanId() == null) {
      return false;
    }
    var parentFrame = activeSpans.get(sc.parentSpanId());
    if (parentFrame != null) {
      builder.setParent(Context.current().with(parentFrame.span()));
      return false;
    }
    var known = knownParents.get(sc.parentSpanId());
    if (known != null) {
      builder.setParent(Context.current().with(Span.wrap(known)));
      return false;
    }
    if (enter.concurrency() == null) {
      var remote = remoteParentContext(sc);
      knownParents.put(sc.parentSpanId(), remote);
      builder.setParent(Context.current().with(Span.wrap(remote)));
      return false;
    }
    builder.setParent(Context.current().with(Span.wrap(reconstructedLocalParent(sc))));
    return true;
  }

  private static io.opentelemetry.api.trace.SpanContext reconstructedLocalParent(SpanContext sc) {
    return io.opentelemetry.api.trace.SpanContext.create(
        sc.traceId().toString(),
        sc.parentSpanId().toString(),
        TraceFlags.fromByte((byte) sc.traceFlags()),
        TraceState.getDefault());
  }

  /** Starts the span with its NarrativeTrace ids on offer, and learns the mode from the first. */
  private Span startMirrored(SpanBuilder builder, SpanContext sc) {
    var offer = IdHandoff.offer(sc, IdHandoff.Source.LIVE);
    Span span;
    try {
      span = builder.startSpan();
    } finally {
      IdHandoff.withdraw(offer);
    }
    if (mirroredIds == null) {
      mirroredIds = sc.spanId().toString().equals(span.getSpanContext().getSpanId());
    }
    return span;
  }

  private static io.opentelemetry.api.trace.SpanContext remoteParentContext(SpanContext sc) {
    return io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
        sc.traceId().toString(),
        sc.parentSpanId().toString(),
        TraceFlags.fromByte((byte) sc.traceFlags()),
        TraceState.getDefault());
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
    frame.span().end(clock.epochNanos(exit.timestampNanos()), TimeUnit.NANOSECONDS);
    if (!Boolean.TRUE.equals(mirroredIds)) {
      knownParents.put(exit.spanContext().spanId(), frame.span().getSpanContext());
    }
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
    parentFrame
        .span()
        .addEvent(name, attrs, clock.epochNanos(exit.timestampNanos()), TimeUnit.NANOSECONDS);
  }

  private void handleExitWithoutEnter(TraceEvent.ExitEvent exit) {
    var sc = exit.spanContext();
    var span =
        tracer
            .spanBuilder(sc.spanId().toString())
            .setStartTimestamp(clock.epochNanos(exit.timestampNanos()), TimeUnit.NANOSECONDS)
            .startSpan();
    SpanContextAttributeMapper.setTraceIdentityAttributes(sc, span);
    SpanContextAttributeMapper.setTraceLevelAttributes(sc, span);
    SpanContextAttributeMapper.setOutcomeAttributes(exit.outcome(), span);
    span.setStatus(StatusCode.ERROR, "orphaned — enter event lost");
    span.end(clock.epochNanos(exit.timestampNanos()), TimeUnit.NANOSECONDS);
  }
}
