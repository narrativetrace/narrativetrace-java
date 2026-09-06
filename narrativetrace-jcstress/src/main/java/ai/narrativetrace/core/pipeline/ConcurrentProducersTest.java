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
import java.util.ArrayList;
import java.util.List;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/**
 * Verifies that concurrent producers do not lose events when the buffer has spare capacity.
 *
 * <p>INTENT: this is the simplest multi-producer safety check. Two actors publish one event each
 * into a capacity-8 buffer, so overflow is not part of the scenario. The arbiter drains the buffer
 * and counts the published events.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>Exactly two events must be observable after both producers run.
 *   <li>Zero or one drained events indicates a lost publication and is forbidden.
 * </ul>
 *
 * <p><b>@pattern</b> Multi-producer publication without contention-induced overwrite
 *
 * <p><b>@llmNote</b> If this test ever permits fewer than two events, the buffer is not safe even
 * in the easiest non-overflow case.
 */
@JCStressTest
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "Both events present")
@Outcome(id = "0", expect = Expect.FORBIDDEN, desc = "Both events lost")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "One event lost")
@State
public class ConcurrentProducersTest {

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(8);

  @Actor
  public void producer1() {
    buffer.put(enterEvent());
  }

  @Actor
  public void producer2() {
    buffer.put(enterEvent());
  }

  @Arbiter
  public void arbiter(I_Result r) {
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    r.r1 = drained.size();
  }

  private static TraceEvent.EnterEvent enterEvent() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        sc, System.nanoTime(), new MethodSignature("S", "m", List.of()));
  }
}
