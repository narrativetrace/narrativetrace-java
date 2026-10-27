/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-26-2 — grafting a malformed node may neither throw an undeclared exception into the
 * traced application nor leave the caller's own span half-open: {@code emitTraceNode} publishes the
 * enter and pushes the span <em>before</em> it can discover the node is unusable, so the caller's
 * own method is recorded as {@code Incomplete} and its return value is attached to the grafted node
 * instead.
 *
 * <p>INTENT: {@code emitTraceNode}'s own {@code @edgeCase} says it plainly — "{@code node} is
 * public API's own input &hellip; that record's {@code children} field is undefended, so a
 * hand-built or replayed node reaching this method is not guaranteed acyclic". Cycles and depth
 * were then bounded through {@code TreeWalk}; the other two ways the same undefended record arrives
 * malformed — a {@code null} child list and a {@code null} signature — were not. Neither is caught
 * by a guard clause at the entry of the method, so the failure surfaces as a {@link
 * NullPointerException} raised from inside the walk, after events have already been published.
 *
 * <p><b>@llmNote</b> The exception is the lesser half. {@code enterEmittedNode} pushes the grafted
 * span onto the caller's own active stack and publishes its enter; when the exit never runs, the
 * caller's next {@code exitMethodWithReturn} pops the <em>grafted</em> span. The traced method
 * therefore returns normally and its trace says it never completed — a wrong narrative, not merely
 * a missing one, which is the line this product does not cross.
 */
class MalformedGraftedNodeTest {

  private static final MethodSignature ORIGIN_CALL =
      new MethodSignature("OrderService", "placeOrder", List.of());

  private static final MethodSignature GRAFTED_CALL =
      new MethodSignature("EmailGateway", "send", List.of());

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();

  @AfterEach
  void clearTheThread() {
    context.reset();
  }

  @Test
  @DisplayName("ADV-2026-10-26-2: a malformed graft must not throw an undeclared error at the host")
  void aNodeWithNoChildListDoesNotRaiseAnUndeclaredNullPointerInTheCaller() {
    var span = context.enterMethod(ORIGIN_CALL);
    var malformed = new TraceNode(GRAFTED_CALL, null, new TraceOutcome.Returned("ok"));

    var thrown = catchThrowable(() -> context.emitTraceNode(malformed, span));

    // B-199 ruled the declared result: a malformed graft is dropped with a diagnostic, never
    // thrown. (AssertJ's isNotInstanceOf rejects a null actual, so "nothing thrown" is asserted
    // directly.)
    assertThat(thrown).as("a malformed graft is dropped, never thrown at the host").isNull();
  }

  @Test
  @DisplayName("ADV-2026-10-26-2: a failed graft must leave the caller's own span balanced")
  void aFailedGraftLeavesTheCallersOwnSpanOpenAndStealsItsReturnValue() {
    var span = context.enterMethod(ORIGIN_CALL);
    var malformed = new TraceNode(GRAFTED_CALL, null, new TraceOutcome.Returned("ok"));
    catchThrowable(() -> context.emitTraceNode(malformed, span));

    // The traced method returns normally; nothing about its own instrumentation changed.
    context.exitMethodWithReturn("done");

    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(1);
    assertThat(roots.get(0).signature().methodName()).isEqualTo("placeOrder");
    assertThat(roots.get(0).outcome())
        .as("a method that returned may never be narrated as never having finished")
        .isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  @DisplayName("B-199: a null signature anywhere in the graft drops it whole, nothing published")
  void aNullSignatureDeepInTheGraftPublishesNothingAndLeavesTheCallerBalanced() {
    var span = context.enterMethod(ORIGIN_CALL);
    var badLeaf = new TraceNode(null, List.of(), new TraceOutcome.Returned("x"));
    var middle = new TraceNode(GRAFTED_CALL, List.of(badLeaf), new TraceOutcome.Returned("ok"));
    var root = new TraceNode(GRAFTED_CALL, List.of(middle), new TraceOutcome.Returned("ok"));

    var thrown = catchThrowable(() -> context.emitTraceNode(root, span));
    var eventsAfterGraft = context.events().size();
    context.exitMethodWithReturn("done");

    assertThat(thrown).isNull();
    assertThat(eventsAfterGraft).as("only the caller's own enter").isEqualTo(1);
    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(1);
    assertThat(roots.get(0).children()).isEmpty();
    assertThat(roots.get(0).outcome()).isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  @DisplayName("B-199: a null element in a child list drops the graft, never throws")
  void aNullChildElementDropsTheGraft() {
    var span = context.enterMethod(ORIGIN_CALL);
    var root =
        new TraceNode(
            GRAFTED_CALL, Arrays.asList((TraceNode) null), new TraceOutcome.Returned("ok"));

    var thrown = catchThrowable(() -> context.emitTraceNode(root, span));
    context.exitMethodWithReturn("done");

    assertThat(thrown).isNull();
    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(1);
    assertThat(roots.get(0).children()).isEmpty();
    assertThat(roots.get(0).outcome()).isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  @DisplayName(
      "B-199: a well-formed graft (null outcome allowed) is still grafted under the caller")
  void aWellFormedGraftIsStillGrafted() {
    var span = context.enterMethod(ORIGIN_CALL);
    var leaf = new TraceNode(GRAFTED_CALL, List.of(), null);
    var root = new TraceNode(GRAFTED_CALL, List.of(leaf), new TraceOutcome.Returned("ok"));

    context.emitTraceNode(root, span);
    context.exitMethodWithReturn("done");

    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(1);
    assertThat(roots.get(0).outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(roots.get(0).children()).hasSize(1);
    assertThat(roots.get(0).children().get(0).children()).hasSize(1);
  }
}
