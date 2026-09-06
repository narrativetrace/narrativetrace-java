/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.export.CanonicalEntryMapper;
import ai.narrativetrace.core.export.CanonicalEntrySerializer;
import ai.narrativetrace.core.export.StructuralProjection;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/**
 * Live AI-safe structural stream: a {@link Flow.Subscriber} that emits every pipeline event as a
 * value-free canonical JSON line.
 *
 * <p>INTENT: The production emission path of the Level-1 structural artifact (ADR-002 "Structure
 * Only"; TODO item 6 milestone 4). Subscribe it to {@code BufferedEventConsumer}'s publisher seam;
 * each event maps to a canonical entry ({@link CanonicalEntryMapper#fromEvent}), passes through
 * {@link StructuralProjection#project} — parameter values become {@code [ELIDED]}, return values
 * and exception messages are dropped — and serializes to one JSON line on the sink (default: the
 * {@value #LOGGER_NAME} SLF4J logger at {@code INFO}, so ordinary appender configuration routes the
 * AI stream wherever it should go).
 *
 * <p>Stateless by design: no per-trace state, nothing to evict, no ordering assumptions — safety
 * and shape are per-event properties of the projection, pinned by the jqwik property in {@code
 * StructuralSubscriberSafetyProperties}.
 *
 * <p><b>@llmNote</b> The async pipeline path is lossy under load by design (drain-loop shedding,
 * bounded offer timeout) — this subscriber renders what arrives and never blocks the capture path.
 * The projection runs LAST, on fully-valued entries; never pre-strip values upstream (Pro levels
 * L2–L4 replace the per-field policy while reading the same input).
 */
public final class StructuralSubscriber implements Flow.Subscriber<TraceEvent> {

  /** Logger carrying the structural stream; route it with ordinary appender configuration. */
  public static final String LOGGER_NAME = "narrativetrace.ai.structural";

  private final Consumer<String> sink;

  /** Creates a subscriber emitting to the {@value #LOGGER_NAME} SLF4J logger at {@code INFO}. */
  public StructuralSubscriber() {
    this(LoggerFactory.getLogger(LOGGER_NAME)::info);
  }

  /**
   * Creates a subscriber with an explicit sink.
   *
   * @param sink receives one serialized structural JSON line per event
   */
  public StructuralSubscriber(Consumer<String> sink) {
    if (sink == null) {
      throw new IllegalArgumentException("sink must not be null");
    }
    this.sink = sink;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(TraceEvent event) {
    sink.accept(
        CanonicalEntrySerializer.toJson(
            StructuralProjection.project(CanonicalEntryMapper.fromEvent(event))));
  }

  @Override
  public void onError(Throwable throwable) {
    // Best-effort derived view: a failed upstream ends the stream; nothing to clean up.
  }

  @Override
  public void onComplete() {
    // Stateless — nothing to flush.
  }
}
