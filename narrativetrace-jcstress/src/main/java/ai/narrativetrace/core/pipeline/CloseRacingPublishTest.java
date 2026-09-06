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
 * Verifies that an event published while the consumer is closing is neither lost nor uncounted.
 *
 * <p>INTENT: Shutdown is not quiet. A servlet request still in a traced method while the context
 * closes publishes into a consumer that is ending, and the three ways that can go wrong are all
 * silent: the event is dropped without being counted, the event is retained but unreachable, or the
 * closing path throws into the application thread that was only trying to finish a request.
 *
 * <p>One actor publishes a single event into a four-slot ring — no overflow, so no loss mode is
 * legitimately available — while the other closes the consumer. The arbiter then does what a late
 * capture does: flush, and read.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1, 0}: the only acceptable outcome. Whether the close's own final drain took the
 *       event or the arbiter's flush did, it is in the store and nothing is counted as dropped.
 *   <li>{@code 0, 0}: forbidden — the event vanished, and the loss counter says nothing happened.
 *       This is the shape the brief names: a publish after close, dropped without being counted.
 *   <li>{@code 0, 1}: forbidden here. Counting the event as dropped would at least be honest, but
 *       this ring cannot overflow and this consumer has no other loss mode, so a drop is a defect
 *       wearing an excuse.
 * </ul>
 *
 * <p><b>@edgeCase</b> The arbiter's flush is load-bearing, and it is also how this scenario found a
 * real defect: {@code flush()} after {@code close()} handed the event to a closed {@code
 * SubmissionPublisher}, whose {@code offer} answers with {@link IllegalStateException} — and
 * nothing on the flush path wraps it, so an ended subscription became the caller's exception. Fixed
 * 2026-09-01; the deterministic proof is {@code
 * BufferedEventConsumerTest.aFlushAfterCloseIsNotTheCallersException}, this is the racing one.
 *
 * <p><b>@llmNote</b> The consumer is built with {@code startConsumer=false}, the form every default
 * topology uses. It starts no drain thread and registers no shutdown hook, which is what makes it
 * usable in a scenario jcstress instantiates millions of times; the thread-started form would leak
 * a thread and a strong GC root per iteration.
 *
 * <p><b>@pattern</b> Lifecycle end racing publication
 */
@JCStressTest
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE, desc = "Event retained, nothing counted as lost")
@Outcome(
    id = "0, 0",
    expect = Expect.FORBIDDEN,
    desc = "Published after close, dropped without being counted")
@Outcome(
    id = "0, [1-9][0-9]*",
    expect = Expect.FORBIDDEN,
    desc = "Counted as dropped by a consumer that has no loss mode here")
@State
public class CloseRacingPublishTest {

  private final BufferedEventConsumer consumer = new BufferedEventConsumer(4, false);
  private final TraceEvent event = StressEvents.tagged(0);

  @Actor
  public void publisher() {
    consumer.accept(event);
  }

  @Actor
  public void closer() {
    consumer.close();
  }

  @Arbiter
  public void arbiter(II_Result r) {
    consumer.flush();
    r.r1 = consumer.events().size();
    r.r2 = (int) consumer.droppedCount();
  }
}
