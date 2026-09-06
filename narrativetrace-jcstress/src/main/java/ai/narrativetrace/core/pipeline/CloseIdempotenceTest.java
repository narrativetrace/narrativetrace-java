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
 * Verifies that two threads closing at once close once, and that the last drain still happens.
 *
 * <p>INTENT: A consumer is closed from more than one place by design — the shutdown hook, the
 * framework's lifecycle callback, and a test's teardown can all reach it, and under a JVM shutdown
 * two of them can arrive together. The guard is a compare-and-set, and what it must protect is not
 * merely "do not close twice": the winner's final drain is the last chance the events still in the
 * ring have to reach the store, so a loser that returns early must not return <em>before</em> that
 * drain has happened for anyone who then reads.
 *
 * <p>The state publishes one event before either actor runs, so there is always something for the
 * closing drain to find. {@code r1} is what the store holds afterwards; {@code r2} is the dropped
 * count.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1, 0}: the only acceptable outcome — exactly one copy of the event, nothing counted
 *       as lost, regardless of which actor won the compare-and-set.
 *   <li>{@code 2, 0}: forbidden — both closes drained, which means two consumers walked the ring
 *       and delivered the same slot twice.
 *   <li>{@code 0, [01]}: forbidden — neither close drained, or the event was lost between them.
 * </ul>
 *
 * <p><b>@pattern</b> Idempotent lifecycle end under contention
 *
 * <p><b>@llmNote</b> {@code closeIsIdempotentWhenCalledConcurrently} in {@code
 * BufferedEventConsumerTest} asks whether ten threads can close without error. This asks the harder
 * question — whether the events survive it.
 */
@JCStressTest
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE, desc = "Closed once, the last drain happened")
@Outcome(id = "2, 0", expect = Expect.FORBIDDEN, desc = "Both closes drained: the event doubled")
@Outcome(id = "0, [01]", expect = Expect.FORBIDDEN, desc = "The last drain lost the event")
@State
public class CloseIdempotenceTest {

  private final BufferedEventConsumer consumer = new BufferedEventConsumer(4, false);

  public CloseIdempotenceTest() {
    TraceEvent event = StressEvents.tagged(0);
    consumer.accept(event);
  }

  @Actor
  public void closer1() {
    consumer.close();
  }

  @Actor
  public void closer2() {
    consumer.close();
  }

  @Arbiter
  public void arbiter(II_Result r) {
    r.r1 = consumer.events().size();
    r.r2 = (int) consumer.droppedCount();
  }
}
