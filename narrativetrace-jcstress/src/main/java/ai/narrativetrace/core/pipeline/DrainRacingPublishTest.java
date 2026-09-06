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
 * Verifies that a drain running against a live producer sees a prefix, never a tear.
 *
 * <p>INTENT: This is the shape the default topology actually runs: one thread publishing while
 * another drains on demand from {@code flush()}. One producer publishes eight events into a
 * four-slot ring — so the drain is racing overwrite as well as publication — while a second actor
 * drains once, mid-flight. The arbiter drains the remainder into the same ledger, so a delivery
 * counted by both is caught.
 *
 * <p>Three things are asserted at once, because they fail differently:
 *
 * <ul>
 *   <li><b>Accounting</b> — {@code r1} is {@code published - delivered - shed} and must be zero.
 *       With a single producer there is no stalled claim to strand the tail, so unlike the
 *       multi-producer scenarios this has no acceptable non-zero residue.
 *   <li><b>Integrity</b> — no null, no duplicate, no out-of-range event. A slot's event field is
 *       written before its sequence is released and read after that sequence is acquired; if that
 *       pairing were broken, a drain would hand back the previous generation's event or a null.
 *   <li><b>Prefix consistency</b> — one producer claims in publication order, so the tags a drain
 *       delivers must strictly increase. A drain that jumped a gap without counting it, or replayed
 *       a slot, shows up here even when the totals happen to balance.
 * </ul>
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 0, 1}: the only acceptable outcome, however the drain and the producer interleave —
 *       whether the drain runs before the first publication, between two of them, or after all
 *       eight.
 *   <li>Non-zero {@code r1}: forbidden. Positive means events vanished uncounted; negative means an
 *       event was both delivered and counted as shed.
 *   <li>{@code r2 = 0}: forbidden — a torn read, a duplicate delivery, or a window that was not a
 *       prefix.
 * </ul>
 *
 * <p><b>@pattern</b> Concurrent drain against a live producer
 *
 * <p><b>@edgeCase</b> Exactly one actor drains. {@link BoundedEventBuffer} is
 * multi-producer/single-consumer, and a second concurrent drain would break the contract the
 * scenario is measuring rather than test it — the arbiter's drain is safe because jcstress runs it
 * only after every actor has finished.
 */
@JCStressTest
@Outcome(id = "0, 1, 1", expect = Expect.ACCEPTABLE, desc = "Prefix delivered, accounting exact")
@Outcome(
    id = "[1-9][0-9]*, [01], [01]",
    expect = Expect.FORBIDDEN,
    desc = "Events vanished: neither delivered nor counted as shed")
@Outcome(
    id = "-[0-9]+, [01], [01]",
    expect = Expect.FORBIDDEN,
    desc = "Over-counted: an event both delivered and counted as shed")
@Outcome(
    id = "[0-9-]+, 0, [01]",
    expect = Expect.FORBIDDEN,
    desc = "Torn read: a null, a duplicate, or an event delivered under the wrong index")
@Outcome(
    id = "[0-9-]+, 1, 0",
    expect = Expect.FORBIDDEN,
    desc = "Non-prefix window: the tags a single producer published arrived out of order")
@State
public class DrainRacingPublishTest {

  private static final int PUBLISHED = 8;

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final DeliveryTally tally = new DeliveryTally(PUBLISHED);

  @Actor
  public void producer() {
    for (int i = 0; i < PUBLISHED; i++) {
      buffer.put(events[i]);
    }
  }

  @Actor
  public void consumer() {
    buffer.drain(tally);
  }

  @Arbiter
  public void arbiter(III_Result r) {
    buffer.drain(tally);
    r.r1 = (int) (PUBLISHED - tally.delivered() - buffer.overwrittenCount());
    r.r2 = tally.intact() ? 1 : 0;
    r.r3 = tally.monotonic() ? 1 : 0;
  }
}
