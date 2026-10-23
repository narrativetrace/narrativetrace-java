/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Converts {@link System#nanoTime()} readings to Unix-epoch nanoseconds.
 *
 * <p>INTENT: Trace events carry the monotonic clock; OpenTelemetry timestamps are epoch-based. One
 * anchor pairs a wall-clock instant with a monotonic reading, and every event time is placed at its
 * offset from that anchor.
 *
 * <p><b>@edgeCase</b> An event captured before the anchor has a negative offset and converts to an
 * epoch time before the anchor's.
 */
final class MonotonicToEpoch {

  private final long anchorEpochNanos;
  private final long anchorNanos;

  /** Anchors at the current wall-clock instant and the current monotonic reading. */
  MonotonicToEpoch() {
    this(Instant.now(), System.nanoTime());
  }

  MonotonicToEpoch(Instant anchorInstant, long anchorNanos) {
    this.anchorEpochNanos =
        TimeUnit.SECONDS.toNanos(anchorInstant.getEpochSecond()) + anchorInstant.getNano();
    this.anchorNanos = anchorNanos;
  }

  /** Returns the epoch nanoseconds for a monotonic reading taken on the same JVM. */
  long epochNanos(long eventNanos) {
    return anchorEpochNanos + (eventNanos - anchorNanos);
  }
}
