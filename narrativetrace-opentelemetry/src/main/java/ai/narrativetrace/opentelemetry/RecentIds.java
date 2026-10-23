/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded memory of the span ids a {@link NarrativeTraceIdGenerator} has mirrored, and by which
 * path.
 *
 * <p>INTENT: The guard against two OpenTelemetry spans sharing one id. With mirrored ids, a span
 * exported live and the same span exported again from a captured tree would otherwise both carry
 * the NarrativeTrace span id.
 *
 * <p><b>@edgeCase</b> Bounded: the oldest id is forgotten first, so a repeat later than {@code
 * capacity} mirrored spans is no longer recognised. That is the documented limit of the guard, the
 * price of a memory that cannot grow with traffic.
 */
final class RecentIds {

  private final int capacity;
  private final ConcurrentHashMap<String, IdHandoff.Source> sources = new ConcurrentHashMap<>();
  private final Queue<String> order = new ConcurrentLinkedQueue<>();
  private final AtomicInteger size = new AtomicInteger();

  RecentIds(int capacity) {
    this.capacity = capacity;
  }

  /**
   * Claims an id for one path.
   *
   * @return {@code null} when the id was new and is now claimed by {@code source}; otherwise the
   *     path that claimed it first (the id must not be mirrored again)
   */
  IdHandoff.Source claim(String spanId, IdHandoff.Source source) {
    var earlier = sources.putIfAbsent(spanId, source);
    if (earlier != null) {
      return earlier;
    }
    order.add(spanId);
    if (size.incrementAndGet() > capacity) {
      var oldest = order.poll();
      if (oldest != null) {
        sources.remove(oldest);
        size.decrementAndGet();
      }
    }
    return null;
  }
}
