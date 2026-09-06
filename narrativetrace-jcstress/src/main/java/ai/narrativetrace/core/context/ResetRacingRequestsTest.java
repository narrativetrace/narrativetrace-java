/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.III_Result;

/**
 * Verifies that two requests sharing one context end cleanly and end alone.
 *
 * <p>INTENT: One {@code ThreadLocalNarrativeContext} serves every concurrent request in a servlet
 * or Micronaut singleton, and {@code reset()} is what a filter calls in its {@code finally}. It has
 * two obligations that pull in opposite directions: it must remove <em>everything</em> its request
 * could report — the spans it created, the ones its async workers handed over, the retained events
 * behind them — and it must touch <em>nothing</em> belonging to a request running at the same
 * moment. Reaching too far deletes a live request's evidence; reaching too little leaks per
 * request, forever, in exactly the long-running process this method exists for.
 *
 * <p>Both actors run a whole request against the same context on their own threads: enter, exit,
 * capture their own events, reset. The arbiter then asks what the process is still holding.
 *
 * <p>{@code r1} and {@code r2} are what each request saw of itself; {@code r3} is the residue —
 * retained events plus retained span contexts — after both have finished.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 2, 2, 0}: the only fully correct outcome. Each request saw its own enter and exit
 *       and nothing else, and nothing outlived either of them.
 *   <li>{@code r1} or {@code r2} below two, with {@code r3 = 0}: interesting but acceptable. The
 *       capture's bounded spin gave up while the other thread held a claimed, unwritten ring slot;
 *       the durable synchronous path narrated the call regardless, and nothing leaked.
 *   <li>{@code r1} or {@code r2} above two: forbidden — a request saw another request's events,
 *       which is the isolation rule that lets one context serve a whole server.
 *   <li>{@code r3} above zero: forbidden — a finished request left spans or events behind.
 * </ul>
 *
 * <p><b>@pattern</b> Request-scoped teardown under concurrency
 *
 * <p><b>@edgeCase</b> The residue check is why the retention is built with a 16-slot ring rather
 * than the 65,536-slot default: the assertion is about what is left, and a state that allocated
 * 1.75 MB per iteration would make jcstress measure the allocator.
 *
 * <p><b>@llmNote</b> Both actors reset, deliberately. A scenario where only one resets would leak a
 * {@code ThreadLocal} entry per iteration on the other actor's thread, and jcstress reuses those
 * threads across millions of states.
 */
@JCStressTest
@Outcome(id = "2, 2, 0", expect = Expect.ACCEPTABLE, desc = "Both requests complete and clean")
@Outcome(
    id = "[01], [012], 0",
    expect = Expect.ACCEPTABLE_INTERESTING,
    desc = "A capture's bounded drain wait was exhausted; nothing leaked")
@Outcome(
    id = "[012], [01], 0",
    expect = Expect.ACCEPTABLE_INTERESTING,
    desc = "A capture's bounded drain wait was exhausted; nothing leaked")
@Outcome(
    id = "[3-9][0-9]*, [0-9]+, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "A request saw another request's events")
@Outcome(
    id = "[0-9]+, [3-9][0-9]*, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "A request saw another request's events")
@Outcome(
    id = "[0-9]+, [0-9]+, [1-9][0-9]*",
    expect = Expect.FORBIDDEN,
    desc = "A finished request left spans or events behind")
@State
public class ResetRacingRequestsTest {

  private static final int RING = 16;

  private final BufferedEventConsumer retention = new BufferedEventConsumer(RING, false);
  private final DualPathPipeline pipeline = new DualPathPipeline(null, retention);
  private final ThreadLocalNarrativeContext context =
      new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

  private final MethodSignature order = new MethodSignature("OrderService", "place", List.of());
  private final MethodSignature payment =
      new MethodSignature("PaymentService", "charge", List.of());

  private int seenByOrder;
  private int seenByPayment;

  @Actor
  public void orderRequest() {
    seenByOrder = runRequest(order);
  }

  @Actor
  public void paymentRequest() {
    seenByPayment = runRequest(payment);
  }

  private int runRequest(MethodSignature signature) {
    context.enterMethod(signature);
    context.exitMethodWithReturn("\"ok\"");
    int own = context.events().size();
    context.reset();
    return own;
  }

  @Arbiter
  public void arbiter(III_Result r) {
    pipeline.flush();
    r.r1 = seenByOrder;
    r.r2 = seenByPayment;
    r.r3 = pipeline.events().size() + context.retainedSpanCount();
  }
}
