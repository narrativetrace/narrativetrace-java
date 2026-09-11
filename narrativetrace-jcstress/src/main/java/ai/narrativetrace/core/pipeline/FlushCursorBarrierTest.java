/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Verifies that the flush barrier's predicate never lies: {@code consumedPast} of a cursor read
 * after one's own {@code put} may only answer "yes" once that event has actually been delivered.
 *
 * <p>INTENT: This is the seam {@code BufferedEventConsumer.flush()} stakes its barrier on. A
 * capture that has finished its own publications reads the claim horizon, drains, and trusts {@code
 * consumedPast(horizon)} to mean "everything I published is in the store" — the collect path's
 * whole no-silent-loss guarantee reduces to that implication. The scenario races the one
 * interleaving that used to break it: a second producer caught between claiming a slot and writing
 * it, which stops the drain below the horizon.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code r1 = 1}: the flusher's drain got past its horizon and its own event had arrived —
 *       the barrier predicate held.
 *   <li>{@code r1 = 2}: the drain stopped below the horizon at the racing producer's
 *       claimed-but-unwritten slot. The interesting interleaving: the predicate honestly says "not
 *       yet", which is what tells a real flush to keep waiting instead of returning around the
 *       stall.
 *   <li>{@code r1 = 0}: forbidden — {@code consumedPast} claimed the horizon was consumed while the
 *       flusher's own event had not been delivered. This is the silent-loss shape.
 *   <li>{@code r2 = 0}: both published events were delivered exactly once by the end; any residue
 *       means an event vanished or doubled.
 * </ul>
 *
 * <p><b>@pattern</b> Barrier predicate racing a stalled publication
 *
 * <p><b>@edgeCase</b> The flusher is the only actor that drains — the ring is
 * multi-producer/single-consumer — and the arbiter's drain runs after every actor has finished.
 * Capacity 8 with two events keeps overwrite out of the scenario: with no lapping possible,
 * "consumed past" can only be satisfied by delivery, which is exactly the implication under test.
 */
@JCStressTest
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE, desc = "Horizon consumed, own event delivered")
@Outcome(
    id = "2, 0",
    expect = Expect.ACCEPTABLE,
    desc = "Drain honestly stopped at the racing claim; a flush would keep waiting")
@Outcome(
    id = "0, [0-9-]+",
    expect = Expect.FORBIDDEN,
    desc = "consumedPast lied: horizon reported consumed with the flusher's event undelivered")
@Outcome(
    id = "[12], [1-9-][0-9]*",
    expect = Expect.FORBIDDEN,
    desc = "Accounting residue: an event vanished or was delivered twice")
@State
public class FlushCursorBarrierTest {

  private static final int PUBLISHED = 2;

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(8);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final ArrayList<TraceEvent> delivered = new ArrayList<>(PUBLISHED);

  @Actor
  public void racingProducer() {
    buffer.put(events[0]);
  }

  @Actor
  public void flusher(II_Result r) {
    buffer.put(events[1]);
    long horizon = buffer.cursor();
    buffer.drain(delivered::add);
    if (buffer.consumedPast(horizon)) {
      r.r1 = delivered.contains(events[1]) ? 1 : 0;
    } else {
      r.r1 = 2;
    }
  }

  @Arbiter
  public void arbiter(II_Result r) {
    buffer.drain(delivered::add);
    r.r2 = PUBLISHED - delivered.size();
    if (!delivered.contains(events[0]) || !delivered.contains(events[1])) {
      r.r2 = -1;
    }
  }
}
