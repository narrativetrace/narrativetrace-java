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
 * Verifies that a capture flushing while the consumer closes cannot walk the ring twice.
 *
 * <p>INTENT: The ring is multi-producer/<em>single</em>-consumer: {@code consumerIndex} is a plain
 * field, advanced with no atomicity, because exactly one thread is supposed to be draining. Every
 * drain the consumer performs is therefore serialised on its monitor — except one. This scenario
 * puts a request thread's {@code flush()} against a shutdown thread's {@code close()} and asks
 * whether the two drains stay out of each other's way.
 *
 * <p>Two events are published before either actor runs. {@code r1} is how many events the store
 * holds at the end; {@code r2} says whether they are the two distinct ones, in order.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 2, 1}: the only acceptable outcome — both events, exactly once each, whichever drain
 *       reached them first.
 *   <li>{@code 3, 0} or {@code 4, 0}: forbidden — two drains read the same {@code consumerIndex}
 *       and delivered the same slot, so the store holds a call that happened once as if it happened
 *       twice.
 *   <li>{@code [01], [01]}: forbidden — an event lost between the two drains, which is the same
 *       lost update seen from the other side.
 * </ul>
 *
 * <p><b>@edgeCase</b> This is where the second defect of the run showed up: {@code close()} called
 * the drain directly instead of through the synchronised {@code flush()}, so the single-consumer
 * rule held for every caller except the one that runs at shutdown, exactly when a request is most
 * likely to be capturing. Fixed 2026-09-01 by routing every drain through the one monitor.
 *
 * <p><b>@pattern</b> Single-consumer discipline across lifecycle boundaries
 */
@JCStressTest
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "Both events, exactly once each")
@Outcome(
    id = "[34], [01]",
    expect = Expect.FORBIDDEN,
    desc = "Two drains delivered the same slot: a call recorded twice")
@Outcome(
    id = "[01], [01]",
    expect = Expect.FORBIDDEN,
    desc = "An event lost between two racing drains")
@Outcome(id = "2, 0", expect = Expect.FORBIDDEN, desc = "Both events, but not the two published")
@State
public class CloseRacingFlushTest {

  private static final int PUBLISHED = 2;

  private final BufferedEventConsumer consumer = new BufferedEventConsumer(4, false);

  public CloseRacingFlushTest() {
    TraceEvent[] events = StressEvents.sequence(PUBLISHED);
    for (TraceEvent event : events) {
      consumer.accept(event);
    }
  }

  @Actor
  public void closer() {
    consumer.close();
  }

  @Actor
  public void capture() {
    consumer.flush();
  }

  @Arbiter
  public void arbiter(II_Result r) {
    consumer.flush();
    var stored = consumer.events();
    var tally = new DeliveryTally(PUBLISHED);
    stored.forEach(tally);
    r.r1 = tally.delivered();
    r.r2 = tally.intact() && tally.monotonic() ? 1 : 0;
  }
}
