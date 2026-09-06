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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * In-memory store of drained trace events.
 *
 * <p>INTENT: Buffered consumers and subscribers use this as their retained event history.
 *
 * <p><b>@threadSafety</b> All access is serialized on the instance monitor. A {@link
 * BufferedEventConsumer} with a live daemon drain thread calls {@link #add} from that thread while
 * request threads call {@link #events}, {@link #clear}, and {@link #removeSpans}; without
 * synchronization the backing {@link ArrayList} throws {@code ConcurrentModificationException} or
 * corrupts. The lock is uncontended on the synchronous ({@code startConsumer=false}) path.
 */
public final class EventStore {

  private final List<TraceEvent> events = new ArrayList<>();

  public synchronized void add(TraceEvent event) {
    events.add(event);
  }

  public synchronized List<TraceEvent> events() {
    return List.copyOf(events);
  }

  public synchronized void clear() {
    events.clear();
  }

  /**
   * Removes only the events belonging to the given span ids, leaving other traces' events (and
   * group lifecycle events) untouched.
   */
  public synchronized void removeSpans(Set<SpanId> spanIds) {
    events.removeIf(
        event -> {
          var spanId = TraceEvent.spanIdOf(event);
          return spanId != null && spanIds.contains(spanId);
        });
  }
}
