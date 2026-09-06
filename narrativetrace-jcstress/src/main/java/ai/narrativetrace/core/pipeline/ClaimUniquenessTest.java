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
 * Verifies that contending producers never claim the same slot.
 *
 * <p>INTENT: Publication is split in two — {@code getAndAdd} claims an index, then the event is
 * stored into it and the slot's sequence is released. Everything the buffer promises rests on the
 * claim being exclusive: two producers landing on one index would overwrite each other's event
 * inside a ring with room to spare, and the loss would be silent because nothing was shed. Four
 * actors publish one event each into an eight-slot ring, so no overflow is possible and any missing
 * event is a lost claim rather than a shed one.
 *
 * <p>{@code r1} counts the delivered events; {@code r2} says whether all four came back distinct,
 * in range, and non-null, with nothing counted as overwritten.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 4, 1}: the only acceptable outcome — four publications, four distinct events, no
 *       shedding.
 *   <li>Fewer than four: forbidden. With spare capacity there is no mechanism that may lose an
 *       event, so a missing one is a double-claimed slot or a publication that never became
 *       visible.
 *   <li>{@code r2 = 0}: forbidden — a duplicate, a null, or a shed count above zero in a ring that
 *       cannot overflow.
 * </ul>
 *
 * <p><b>@pattern</b> Exclusive slot claim under contention
 *
 * <p><b>@llmNote</b> {@link ConcurrentProducersTest} asks the same question with two actors and
 * counts only. This one adds the identities, so an event delivered twice can no longer hide a
 * different one that was lost.
 */
@JCStressTest
@Outcome(id = "4, 1", expect = Expect.ACCEPTABLE, desc = "Four distinct events, nothing shed")
@Outcome(
    id = "[0-3], [01]",
    expect = Expect.FORBIDDEN,
    desc = "Event lost with capacity to spare: a slot was claimed twice")
@Outcome(
    id = "4, 0",
    expect = Expect.FORBIDDEN,
    desc = "Duplicate, null, or unexpectedly shed event")
@State
public class ClaimUniquenessTest {

  private static final int PUBLISHED = 4;

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(8);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final DeliveryTally tally = new DeliveryTally(PUBLISHED);

  @Actor
  public void producer1() {
    buffer.put(events[0]);
  }

  @Actor
  public void producer2() {
    buffer.put(events[1]);
  }

  @Actor
  public void producer3() {
    buffer.put(events[2]);
  }

  @Actor
  public void producer4() {
    buffer.put(events[3]);
  }

  @Arbiter
  public void arbiter(II_Result r) {
    buffer.drain(tally);
    r.r1 = tally.delivered();
    r.r2 = tally.intact() && buffer.overwrittenCount() == 0 ? 1 : 0;
  }
}
