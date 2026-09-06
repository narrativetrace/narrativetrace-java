/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;

/**
 * Events for the stress scenarios, each carrying its own publication number.
 *
 * <p>INTENT: A ring-buffer scenario has to answer "which events came back?", not merely "how many",
 * or a duplicate delivery and a lost one cancel out in the count. Every event a scenario publishes
 * is tagged with its sequence in {@code timestampNanos} — a field the buffer never reads — so a
 * drain can name exactly what it received.
 *
 * <p>Events are built <em>before</em> the actors start, in the state's field initialisers. An actor
 * that allocates inside the race window widens it, and a wide window is a scenario that stops
 * asking the question it was written for.
 *
 * <p><b>@llmNote</b> The span context and the signature are shared across every event: they are
 * immutable records, nothing in these scenarios reads them, and per-event copies would be the
 * dominant allocation in a state that jcstress builds millions of times.
 *
 * <p><b>@pattern</b> Tagged fixtures for identity-preserving concurrency assertions
 */
final class StressEvents {

  private static final SpanContext SPAN_CONTEXT =
      SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();

  private static final MethodSignature SIGNATURE =
      new MethodSignature("StressSubject", "publish", List.of());

  private StressEvents() {}

  /** An event tagged with {@code sequence}, which {@link #tagOf} reads back. */
  static TraceEvent tagged(long sequence) {
    return new TraceEvent.EnterEvent(SPAN_CONTEXT, sequence, SIGNATURE);
  }

  /** Events tagged {@code 0..count-1}, in that order — a scenario's whole publication plan. */
  static TraceEvent[] sequence(int count) {
    var events = new TraceEvent[count];
    for (int i = 0; i < count; i++) {
      events[i] = tagged(i);
    }
    return events;
  }

  /**
   * The tag a delivered event carries, or {@code -1} for anything that is not one of ours.
   *
   * <p><b>@edgeCase</b> {@code null} answers {@code -1} rather than throwing: a torn read is one of
   * the outcomes these scenarios exist to observe, and observing it must not become an error.
   */
  static long tagOf(TraceEvent event) {
    return event instanceof TraceEvent.EnterEvent enter ? enter.timestampNanos() : -1L;
  }
}
