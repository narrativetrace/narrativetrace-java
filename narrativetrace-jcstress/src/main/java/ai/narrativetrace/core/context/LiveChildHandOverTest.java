/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import java.lang.ref.WeakReference;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Verifies the ruled invariant of item 44: through one side or the other, never twice, never
 * neither.
 *
 * <p>INTENT: A worker's spans reach the thread that captures them by two routes. While the worker's
 * scope is open they are visible because the child stack is registered as a live child; when the
 * scope closes they are visible because the origin adopted them. Scope close crosses from one route
 * to the other, and it does so in two separate steps — adopt, then unregister — with a capture
 * possibly running in between on another thread.
 *
 * <p>That ordering is the entire guarantee. Reversed, there is a window in which the spans belong
 * to neither route and an async subtree drops out of the narrative for as long as it lasts. This
 * scenario runs a capture against exactly that hand-over.
 *
 * <p>{@code r1} is what the capturing actor saw mid-hand-over; {@code r2} is what remains
 * reportable afterwards.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1, 1}: the only acceptable outcome. The worker's one span is reportable during the
 *       hand-over and after it.
 *   <li>{@code 0, ...}: forbidden — the span was invisible during the window, which is the defect
 *       item 44 fixed: an async call the caller ran, waited for, and could not see.
 *   <li>{@code 2, ...}: forbidden — counted through both routes at once. Sets are what make this
 *       impossible; an implementation that summed counts instead would land here.
 * </ul>
 *
 * <p><b>@pattern</b> Two-route visibility across a hand-over
 *
 * <p><b>@edgeCase</b> The child stack is strongly held by the state, so the weak registration
 * cannot be cleared underneath the scenario — a collected registration is a different question, and
 * a deterministic test already owns it.
 *
 * <p><b>@llmNote</b> The actor performs the production sequence verbatim, including that it hands
 * over {@code reportableSpanIds()} rather than the child's own spans: the two sides must name one
 * set, or a nested chain loses a hop.
 */
@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "Visible through one route or the other")
@Outcome(
    id = "0, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "Invisible mid-hand-over: the async subtree drops out of the trace")
@Outcome(
    id = "[2-9][0-9]*, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "Counted through both routes at once")
@Outcome(
    id = "[0-9]+, [02-9][0-9]*",
    expect = Expect.FORBIDDEN,
    desc = "Not reportable once the hand-over finished")
@State
public class LiveChildHandOverTest {

  private static final int CEILING = 16;

  private final TraceStack origin = new TraceStack(CEILING);
  private final TraceStack child = new TraceStack(CEILING);
  private final WeakReference<TraceStack> registration;

  public LiveChildHandOverTest() {
    child.pushActive(SpanId.generate());
    registration = origin.registerLiveChild(child);
  }

  @Actor
  public void workerClosingItsScope() {
    origin.adopt(child.reportableSpanIds());
    origin.unregisterLiveChild(registration);
  }

  @Actor
  public void capture(II_Result r) {
    r.r1 = origin.reportableSpanIds().size();
  }

  @Arbiter
  public void arbiter(II_Result r) {
    r.r2 = origin.reportableSpanIds().size();
  }
}
