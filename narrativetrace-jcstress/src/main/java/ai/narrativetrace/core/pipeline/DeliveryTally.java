/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.util.function.Consumer;

/**
 * What a drain actually delivered — counted, identified, and checked for the impossible.
 *
 * <p>INTENT: The loss-accounting invariant is that every published event is either delivered
 * exactly once or counted as shed, never both and never neither. Answering that needs the
 * identities of the delivered events, so this records the tag of each one against the publication
 * plan and reports three separate facts:
 *
 * <ul>
 *   <li>{@link #delivered()} — how many events reached the drain's action
 *   <li>{@link #intact()} — every delivery was a real, in-range, not-yet-seen event
 *   <li>{@link #monotonic()} — tags arrived in increasing order, i.e. the drain saw a prefix of one
 *       producer's publication order rather than a torn or reordered window
 * </ul>
 *
 * <p><b>@threadSafety</b> Not thread-safe, and deliberately so: the buffer is
 * multi-producer/single-consumer, so one tally belongs to the one thread draining at a time. A
 * scenario shares a single instance between its drain actor and its arbiter — which run in
 * sequence, never together — precisely so a delivery counted twice across the two is caught.
 *
 * <p><b>@pattern</b> Identity-preserving delivery ledger
 */
final class DeliveryTally implements Consumer<TraceEvent> {

  private final boolean[] seen;
  private int delivered;
  private boolean intact = true;
  private boolean monotonic = true;
  private long previousTag = -1L;

  DeliveryTally(int published) {
    this.seen = new boolean[published];
  }

  @Override
  public void accept(TraceEvent event) {
    delivered++;
    long tag = StressEvents.tagOf(event);
    if (tag < 0 || tag >= seen.length || seen[(int) tag]) {
      intact = false;
      return;
    }
    seen[(int) tag] = true;
    monotonic = monotonic && tag > previousTag;
    previousTag = tag;
  }

  int delivered() {
    return delivered;
  }

  /** Whether every delivery was a distinct, in-range, non-null event. */
  boolean intact() {
    return intact;
  }

  /** Whether the delivered tags increased, which single-producer scenarios require. */
  boolean monotonic() {
    return monotonic;
  }
}
