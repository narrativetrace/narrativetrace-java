/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.III_Result;

/**
 * Verifies that a snapshot taken while the store is being appended to is a prefix, not a tear.
 *
 * <p>INTENT: {@link EventStore} is an {@code ArrayList} behind a monitor, and every reader gets
 * {@code List.copyOf} of it. That is the whole design, and it is load-bearing in a way that is easy
 * to lose: a drain thread appends while request threads snapshot, and an unsynchronised {@code
 * ArrayList} answers that with a {@code ConcurrentModificationException}, a null tail, or an array
 * that was reallocated mid-copy. Swapping in a "faster" list, or dropping the copy to return the
 * live list, would each break this quietly.
 *
 * <p>One actor appends four tagged events in order; the other takes one snapshot and reads it.
 * {@code r1} is the snapshot's size, {@code r2} says the snapshot was a valid prefix — distinct,
 * in-range, non-null events with increasing tags — and {@code r3} is the store's size afterwards.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code n, 1, 4} for any {@code n} from 0 to 4: acceptable. Every prefix is a legitimate
 *       observation; which one you get is scheduling, not semantics.
 *   <li>{@code r2 = 0}: forbidden — the snapshot contained a null, a duplicate, or events out of
 *       append order, i.e. a partially visible list rather than a prefix of one.
 *   <li>{@code r3} other than 4: forbidden — an append was lost, or the copy disturbed the store.
 * </ul>
 *
 * <p><b>@pattern</b> Prefix-consistent snapshot of an append-only store
 *
 * <p><b>@edgeCase</b> Prefix consistency is stronger than "no exception". A store that returned a
 * live view would often pass a crash test and still hand a renderer a list that grows while it is
 * being walked, which is how a trace acquires a node that belongs to the next request.
 */
@JCStressTest
@Outcome(
    id = "[0-4], 1, 4",
    expect = Expect.ACCEPTABLE,
    desc = "Snapshot is a prefix of the appends; the store ends complete")
@Outcome(
    id = "[0-9]+, 0, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "Partially visible snapshot: null, duplicate, or out-of-order")
@Outcome(
    id = "[0-9]+, [01], [0-35-9]",
    expect = Expect.FORBIDDEN,
    desc = "The store did not end with every appended event")
@State
public class EventStoreSnapshotTest {

  private static final int APPENDED = 4;

  private final EventStore store = new EventStore();
  private final TraceEvent[] events = StressEvents.sequence(APPENDED);
  private final DeliveryTally observed = new DeliveryTally(APPENDED);

  @Actor
  public void appender() {
    for (TraceEvent event : events) {
      store.add(event);
    }
  }

  @Actor
  public void reader() {
    store.events().forEach(observed);
  }

  @Arbiter
  public void arbiter(III_Result r) {
    r.r1 = observed.delivered();
    r.r2 = observed.intact() && observed.monotonic() ? 1 : 0;
    r.r3 = store.events().size();
  }
}
