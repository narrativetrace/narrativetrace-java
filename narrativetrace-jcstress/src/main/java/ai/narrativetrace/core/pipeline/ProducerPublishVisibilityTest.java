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
import org.openjdk.jcstress.infra.results.III_Result;

/**
 * Verifies eventual visibility after a producer publishes an event.
 *
 * <p>INTENT: the consumer polls twice and the arbiter polls once more. Together those three reads
 * describe whether a single published event becomes visible somewhere after {@code put()} returns.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1,0,0}: acceptable because the first consumer poll saw the event.
 *   <li>{@code 0,1,0}: acceptable because the event became visible on the second poll.
 *   <li>{@code 0,0,1}: acceptable because the arbiter recovered the event after both consumer polls
 *       missed it.
 *   <li>{@code 0,0,0}: forbidden because the event was permanently lost.
 * </ul>
 *
 * <p><b>@pattern</b> Eventual visibility after producer completion
 *
 * <p><b>@edgeCase</b> This scenario allows delayed visibility but not disappearance. A missed first
 * poll is fine; universal miss is not.
 *
 * <p><b>@llmNote</b> Read this test together with {@link PollBeforePublishTest}: both guard against
 * publication-ordering bugs, but this one also documents acceptable delayed visibility.
 */
@JCStressTest
@Outcome(id = "1, 0, 0", expect = Expect.ACCEPTABLE, desc = "First poll got it")
@Outcome(id = "0, 1, 0", expect = Expect.ACCEPTABLE, desc = "Second poll got it")
@Outcome(id = "0, 0, 1", expect = Expect.ACCEPTABLE, desc = "Arbiter recovered it")
@Outcome(id = "0, 0, 0", expect = Expect.FORBIDDEN, desc = "Event permanently lost")
@State
public class ProducerPublishVisibilityTest {

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);

  @Actor
  public void producer() {
    buffer.put(enterEvent());
  }

  @Actor
  public void consumer(III_Result r) {
    TraceEvent first = buffer.poll();
    r.r1 = first != null ? 1 : 0;
    TraceEvent second = buffer.poll();
    r.r2 = second != null ? 1 : 0;
  }

  @Arbiter
  public void arbiter(III_Result r) {
    TraceEvent remaining = buffer.poll();
    r.r3 = remaining != null ? 1 : 0;
  }

  private static TraceEvent.EnterEvent enterEvent() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        sc, System.nanoTime(), new MethodSignature("S", "m", List.of()));
  }
}
