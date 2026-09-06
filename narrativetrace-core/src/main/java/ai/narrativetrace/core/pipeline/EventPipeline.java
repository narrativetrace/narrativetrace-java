/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;

/**
 * Strategy for routing trace events to one or more consumers.
 *
 * <p>INTENT: Context implementations depend on this abstraction so event storage, synchronous
 * listeners, and best-effort buffering can evolve independently from instrumentation.
 */
public interface EventPipeline extends AutoCloseable {

  void publish(TraceEvent event);

  /** Drains buffered events so they become visible via {@link #events()}. */
  default void flush() {}

  /**
   * Whether everything published so far has been drained into {@link #events()}.
   *
   * <p>INTENT: Lets a caller that is about to read the events as a record tell "there is nothing
   * more" from "the last flush could not reach it yet" — see {@link RetainingConsumer#drained()}.
   * Default {@code true} for pipelines that buffer nothing.
   *
   * @return {@code true} when nothing published is still in flight
   */
  default boolean drained() {
    return true;
  }

  /** Returns the events currently visible from this pipeline. */
  default java.util.List<TraceEvent> events() {
    return java.util.List.of();
  }

  /** Clears any retained events and derived state. */
  default void clear() {}

  /**
   * Removes only the retained events belonging to the given span ids.
   *
   * <p>INTENT: Request-scoped cleanup — a finishing request drops its own consumed events without
   * disturbing other in-flight traces. No-op for pipelines that retain nothing.
   *
   * @param spanIds Span ids whose events are dropped; other traces are untouched.
   */
  default void clearSpans(java.util.Set<SpanId> spanIds) {}

  /**
   * Events this pipeline shed rather than retained, process-wide since start. Zero for pipelines
   * that cannot shed (synchronous-only, no-op); the buffered path reports its load-shedding here so
   * a short trace can be explained rather than silently trusted.
   */
  default long droppedEventCount() {
    return 0L;
  }

  @Override
  void close();
}
