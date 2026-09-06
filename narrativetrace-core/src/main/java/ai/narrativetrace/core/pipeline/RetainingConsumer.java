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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Best-effort event consumer that retains published events in memory for querying.
 *
 * <p>INTENT: {@link DualPathPipeline} delegates its store operations ({@code flush}, {@code
 * events}, {@code clear}, span cleanup) to whatever holds the best-effort slot. This contract makes
 * the slot's retention pluggable — the default is {@link BufferedEventConsumer}'s bounded ring
 * buffer, but an alternative transport (e.g. a Disruptor-backed consumer) satisfies the same
 * contract without the pipeline knowing the difference. Retention is in-memory and best-effort —
 * never durable; durability is the synchronous path's concern.
 *
 * <p><b>@layer</b> Pipeline internals. Implementations receive events via {@link #accept} on caller
 * threads and must never block them or let an exception escape into application code.
 */
public interface RetainingConsumer extends Consumer<TraceEvent> {

  /** Drains any internally buffered events so they become visible via {@link #events()}. */
  void flush();

  /**
   * Whether everything published so far has been drained into {@link #events()}.
   *
   * <p>INTENT: A drain cannot consume a slot a producer has claimed but not finished writing, so
   * one {@link #flush()} under concurrent publishing can return with events still outstanding —
   * including the caller's own. A caller that is about to read {@link #events()} as a record (a
   * request capturing its trace, a concurrency helper collecting a worker's roots) asks this and
   * flushes again rather than reporting a story with a hole in it.
   *
   * <p>Default {@code true}, for implementations that retain synchronously and therefore never have
   * anything outstanding.
   *
   * @return {@code true} when nothing published is still in flight
   */
  default boolean drained() {
    return true;
  }

  /**
   * Returns the events retained so far.
   *
   * @return Retained events in publish order. Never null.
   */
  List<TraceEvent> events();

  /** Clears all retained events. */
  void clear();

  /**
   * Removes only the retained events belonging to the given span ids.
   *
   * @param spanIds Span ids whose events are dropped; other traces are untouched.
   */
  void removeSpans(Set<SpanId> spanIds);
}
