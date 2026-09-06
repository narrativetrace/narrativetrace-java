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
 * Measures what a single flush does and does not promise while another thread is publishing.
 *
 * <p>INTENT: The tempting post-condition — "everything published before {@code flush()} returns is
 * in the store" — is <b>false under contention</b>, and this scenario is where that is written
 * down. A drain stops at the first slot a producer has claimed and not yet written, so a thread
 * that publishes its own event <em>behind</em> another thread's claimed slot can flush and not find
 * it. That is not a defect in {@code flush()}; it is why {@code
 * ThreadLocalNarrativeContext.drainPublishedEvents()} spins up to {@code DRAIN_ATTEMPTS} times
 * instead of flushing once, and why a fork collecting 512 workers used to lose whole workers before
 * that spin existed.
 *
 * <p>One actor publishes and immediately flushes, recording what the store then held ({@code r1}).
 * The other publishes once. The arbiter flushes again and records how many events the store ends
 * with ({@code r2}) and whether they are the two distinct published ones ({@code r3}).
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1, 2, 1} and {@code 2, 2, 1}: acceptable — the flushing thread saw its own event,
 *       and possibly the other thread's too.
 *   <li>{@code 0, 2, 1}: interesting but acceptable — the flushing thread saw nothing at all,
 *       because the other producer's claimed slot sat in front of its event. This is the documented
 *       weak post-condition, and the reason capture retries.
 *   <li>{@code r2} other than 2: forbidden — flushing twice must leave exactly what was published.
 *   <li>{@code r3 = 0}: forbidden — a duplicate, a null, or an event that was never published.
 * </ul>
 *
 * <p><b>@pattern</b> Weak flush post-condition under concurrent publication
 *
 * <p><b>@llmNote</b> If the interesting row ever stops appearing, either the ring stopped stopping
 * at unwritten slots or the scenario stopped racing. Both are worth knowing; neither is a pass.
 */
@JCStressTest
@Outcome(id = "1, 2, 1", expect = Expect.ACCEPTABLE, desc = "Flush saw its own event")
@Outcome(id = "2, 2, 1", expect = Expect.ACCEPTABLE, desc = "Flush saw both events")
@Outcome(
    id = "0, 2, 1",
    expect = Expect.ACCEPTABLE_INTERESTING,
    desc = "One flush was not enough: a claimed, unwritten slot blocked the drain")
@Outcome(
    id = "[0-9]+, [013-9], [01]",
    expect = Expect.FORBIDDEN,
    desc = "Two flushes did not leave exactly the two published events")
@Outcome(
    id = "[0-9]+, 2, 0",
    expect = Expect.FORBIDDEN,
    desc = "Duplicate, null, or unpublished event in the store")
@State
public class FlushRacingPublishTest {

  private static final int PUBLISHED = 2;

  private final BufferedEventConsumer consumer = new BufferedEventConsumer(4, false);
  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);

  private int seenByOwnFlush;

  @Actor
  public void capturingPublisher() {
    consumer.accept(events[0]);
    consumer.flush();
    seenByOwnFlush = consumer.events().size();
  }

  @Actor
  public void otherPublisher() {
    consumer.accept(events[1]);
  }

  @Arbiter
  public void arbiter(III_Result r) {
    consumer.flush();
    var tally = new DeliveryTally(PUBLISHED);
    consumer.events().forEach(tally);
    r.r1 = seenByOwnFlush;
    r.r2 = tally.delivered();
    r.r3 = tally.intact() ? 1 : 0;
  }
}
