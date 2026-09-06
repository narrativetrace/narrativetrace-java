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
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Characterizes overwrite behavior when producers outpace a bounded buffer.
 *
 * <p>INTENT: two actors each publish four events into a capacity-4 buffer. The test accepts data
 * loss because the buffer is bounded and best-effort under overflow, but it does not accept data
 * corruption. The arbiter drains the surviving window and records:
 *
 * <ul>
 *   <li>{@code r1}: how many events remain visible after overwrite races
 *   <li>{@code r2}: whether every retained event still contains a valid non-null span id
 * </ul>
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>Any retained count from 1 to 4 is acceptable if retained events are structurally valid.
 *   <li>{@code 0,1} is interesting but acceptable because cross-generation overwrite can leave a
 *       temporary sequence gap.
 *   <li>Any outcome with {@code r2=0} is forbidden because overwrite may drop data, but it must not
 *       corrupt it.
 * </ul>
 *
 * <p><b>@pattern</b> Best-effort retention under overflow
 *
 * <p><b>@edgeCase</b> Sequence continuity can break across generations, so zero retained events is
 * not automatically corruption in this stress scenario.
 *
 * <p><b>@llmNote</b> This test documents an important design choice: bounded buffers may lose old
 * events under pressure, but they must preserve object integrity for whatever survives.
 *
 * <p><b>@edgeCase</b> "Accepts data loss" means the retained <em>count</em> is free, not that the
 * loss is invisible. Every event the arbiter's drain skips past is added to {@link
 * BoundedEventBuffer#overwrittenCount()} and surfaces as {@code TraceLoss.droppedEvents}; this test
 * simply does not assert on that number, because under a race it is whatever the interleaving made
 * it. {@code ShedCountingTest} and {@code TraceLossAccountingTest} pin it deterministically.
 */
@JCStressTest
@Outcome(id = "4, 1", expect = Expect.ACCEPTABLE, desc = "Full window retained")
@Outcome(id = "3, 1", expect = Expect.ACCEPTABLE, desc = "3 events retained")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "2 events retained")
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "1 event retained")
@Outcome(
    id = "0, 1",
    expect = Expect.ACCEPTABLE_INTERESTING,
    desc = "Sequence gap from cross-generation overwrite; best-effort loss")
@Outcome(id = ".*, 0", expect = Expect.FORBIDDEN, desc = "Corrupted event data")
@State
public class OverwriteWindowTest {

  private final BoundedEventBuffer buffer = new BoundedEventBuffer(4);

  @Actor
  public void producer1() {
    for (int i = 0; i < 4; i++) {
      buffer.put(enterEvent());
    }
  }

  @Actor
  public void producer2() {
    for (int i = 4; i < 8; i++) {
      buffer.put(enterEvent());
    }
  }

  @Arbiter
  public void arbiter(II_Result r) {
    var drained = new ArrayList<TraceEvent>();
    buffer.drain(drained::add);
    r.r1 = drained.size();
    boolean allValid = true;
    for (TraceEvent e : drained) {
      if (((TraceEvent.EnterEvent) e).spanContext().spanId() == null) {
        allValid = false;
      }
    }
    r.r2 = allValid ? 1 : 0;
  }

  private static TraceEvent.EnterEvent enterEvent() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(
        sc, System.nanoTime(), new MethodSignature("S", "m", List.of()));
  }
}
