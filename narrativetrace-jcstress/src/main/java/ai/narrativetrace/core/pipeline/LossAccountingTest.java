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
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Verifies that two contending producers cannot make the loss count disagree with reality.
 *
 * <p>INTENT: {@code TraceLoss.droppedEvents} is the number a reader trusts when a trace looks
 * short, and {@link BoundedEventBuffer#overwrittenCount()} is where it comes from. The invariant it
 * has to keep under contention is an accounting one: <b>every published event is either delivered
 * exactly once or counted as shed exactly once — never both, and never neither.</b> Two actors
 * publish eight events each into a four-slot ring, so overflow is guaranteed and most of the events
 * are shed; the arbiter drains what is left and reconciles.
 *
 * <p>{@code r1} is the reconciliation residue, {@code published - delivered - shed}, so zero is the
 * exact accounting. {@code r2} says whether every delivered event was a distinct, in-range,
 * non-null one.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 0, 1}: acceptable, and the normal result — the ring's own count of what it overwrote
 *       plus what came out equals what went in.
 *   <li>{@code n, 1} for positive {@code n}: interesting but acceptable — a producer that stalled
 *       between claiming its slot and writing it leaves a stale sequence behind, and a drain that
 *       stops there strands the tail without counting it. This is the same cross-generation gap
 *       {@link OverwriteWindowTest} documents, seen through the accounting instead of the count.
 *   <li>Negative {@code r1}: forbidden. The residue can only go negative if an event was delivered
 *       <em>and</em> counted as shed, which is the one direction that makes {@code TraceLoss} lie
 *       about a trace that is in fact complete.
 *   <li>{@code r2 = 0}: forbidden. Overflow may drop events; it may never hand back a null, a
 *       duplicate, or an event from a slot whose sequence said otherwise.
 * </ul>
 *
 * <p><b>@pattern</b> Loss accounting under multi-producer overflow
 *
 * <p><b>@edgeCase</b> A duplicate delivery is reachable in theory, not only through a broken
 * consumer index: a producer preempted for a whole ring lap between claiming its slot and storing
 * its event can land that store between a later generation's sequence release and the consumer's
 * read of the slot. The window is two instructions wide and has never been observed. It is
 * forbidden here because the buffer's documented contract is that overwrite loses data without
 * corrupting it — if this ever fires, the finding is the interleaving, not the assertion.
 *
 * <p><b>@llmNote</b> Read with {@link SaturatedRingAccountingTest} (the same invariant at four
 * contending producers) and {@link DrainRacingPublishTest} (the same invariant with a drain running
 * concurrently rather than only afterwards).
 */
@JCStressTest
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE, desc = "Delivered + shed == published exactly")
@Outcome(
    id = "[1-9][0-9]*, 1",
    expect = Expect.ACCEPTABLE_INTERESTING,
    desc = "Cross-generation stall stranded the tail; best-effort loss, uncounted")
@Outcome(
    id = "-[0-9]+, [01]",
    expect = Expect.FORBIDDEN,
    desc = "Over-counted: an event both delivered and counted as shed")
@Outcome(
    id = "[0-9-]+, 0",
    expect = Expect.FORBIDDEN,
    desc = "Null, duplicate, or out-of-range event delivered")
@State
public class LossAccountingTest {

  private static final int PER_PRODUCER = 8;
  private static final int PUBLISHED = 2 * PER_PRODUCER;

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final DeliveryTally tally = new DeliveryTally(PUBLISHED);

  @Actor
  public void producer1() {
    for (int i = 0; i < PER_PRODUCER; i++) {
      buffer.put(events[i]);
    }
  }

  @Actor
  public void producer2() {
    for (int i = PER_PRODUCER; i < PUBLISHED; i++) {
      buffer.put(events[i]);
    }
  }

  @Arbiter
  public void arbiter(II_Result r) {
    buffer.drain(tally);
    r.r1 = (int) (PUBLISHED - tally.delivered() - buffer.overwrittenCount());
    r.r2 = tally.intact() ? 1 : 0;
  }
}
