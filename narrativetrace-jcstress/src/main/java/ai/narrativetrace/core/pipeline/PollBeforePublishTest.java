/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Verifies that a racy poll cannot permanently lose a single published event.
 *
 * <p>INTENT: one actor publishes once, another actor polls once without coordination, and the
 * arbiter performs one final recovery poll. This models the classic "consumer checked too early"
 * race.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 0,1}: acceptable because the consumer raced early and the arbiter later recovered
 *       the event.
 *   <li>{@code 1,0}: acceptable because the consumer received the event directly.
 *   <li>{@code 0,0}: forbidden because the event disappeared from both polls.
 * </ul>
 *
 * <p><b>@pattern</b> Single publication versus early poll
 *
 * <p><b>@llmNote</b> This is the regression test for the dangerous case where publication ordering
 * lets a consumer observe "nothing yet" and still lose the event forever.
 */
@JCStressTest
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE, desc = "Poll missed, arbiter found event")
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE, desc = "Poll found event directly")
@Outcome(id = "0, 0", expect = Expect.FORBIDDEN, desc = "Event permanently lost")
@State
public class PollBeforePublishTest {

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);

  @Actor
  public void producer() {
    buffer.put(enterEvent());
  }

  @Actor
  public void consumer(II_Result r) {
    TraceEvent event = buffer.poll();
    r.r1 = event != null ? 1 : 0;
  }

  @Arbiter
  public void arbiter(II_Result r) {
    TraceEvent event = buffer.poll();
    r.r2 = event != null ? 1 : 0;
  }

  private static TraceEvent.EnterEvent enterEvent() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        sc, System.nanoTime(), new MethodSignature("S", "m", List.of()));
  }
}
