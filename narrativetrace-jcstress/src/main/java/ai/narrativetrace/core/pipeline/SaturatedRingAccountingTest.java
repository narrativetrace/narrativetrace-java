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
 * Holds the loss-accounting invariant at the highest contention this suite runs.
 *
 * <p>INTENT: {@link LossAccountingTest} asks the question with two producers; this asks it with
 * four, each publishing four events into a four-slot ring — sixteen publications against a ring
 * that can hold four, with every claim contending on the same {@code getAndAdd}. Everything else is
 * identical, deliberately: the contract must not soften as threads are added, and a scenario that
 * changed two variables at once could not say which one moved the result.
 *
 * <p>{@code r1} is {@code published - delivered - shed}; {@code r2} says whether every delivery was
 * a distinct, in-range, non-null event.
 *
 * <p>Expected contract — the same four rows as the two-producer scenario:
 *
 * <ul>
 *   <li>{@code 0, 1}: acceptable and normal.
 *   <li>{@code n, 1}: interesting — a producer stalled between claim and store stranded the tail.
 *   <li>Negative {@code r1}: forbidden — an event both delivered and counted as shed.
 *   <li>{@code r2 = 0}: forbidden — corrupted, duplicated or lost identity.
 * </ul>
 *
 * <p><b>@pattern</b> Loss accounting at four contending producers
 *
 * <p><b>@edgeCase</b> Four actors need four hardware threads. jcstress schedules them itself, but a
 * two-CPU runner oversubscribes rather than skips, which makes the interleavings coarser without
 * making the assertion weaker — the invariant is not a timing one.
 *
 * <p><b>@llmNote</b> Contention degree is the variable this file owns. One producer racing a drain
 * is {@link DrainRacingPublishTest}; two producers are {@link LossAccountingTest}; sixteen
 * publications from four threads are here.
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
public class SaturatedRingAccountingTest {

  private static final int PER_PRODUCER = 4;
  private static final int PUBLISHED = 4 * PER_PRODUCER;

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final DeliveryTally tally = new DeliveryTally(PUBLISHED);

  @Actor
  public void producer1() {
    publish(0);
  }

  @Actor
  public void producer2() {
    publish(1);
  }

  @Actor
  public void producer3() {
    publish(2);
  }

  @Actor
  public void producer4() {
    publish(3);
  }

  private void publish(int producer) {
    int from = producer * PER_PRODUCER;
    for (int i = from; i < from + PER_PRODUCER; i++) {
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
