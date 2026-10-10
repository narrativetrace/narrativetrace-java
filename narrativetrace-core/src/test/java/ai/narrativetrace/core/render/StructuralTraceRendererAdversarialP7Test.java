/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Id numbering and fork/fire-and-forget/cycle edge cases of {@link StructuralTraceRenderer}. */
class StructuralTraceRendererAdversarialP7Test {

  private final StructuralTraceRenderer renderer = new StructuralTraceRenderer();

  @Test
  void forkMembersSharingASignatureKeepCaptureOrderAndTheirOwnChildIds() {
    var fork = new ConcurrencyInfo("fork-1", "pool-1", 1, false, ConcurrencyKind.FORK_JOIN);
    var first = node("Svc", "run", List.of(leaf("Y", "first")), fork);
    var second = node("Svc", "run", List.of(leaf("Y", "second")), fork);
    var root = node("Co", "go", List.of(first, second), null);

    assertThat(renderer.render(new DefaultTraceTree(List.of(root))))
        .isEqualTo(
            "#1 - Co.go()\n"
                + "  ~ fork [2]\n"
                + "    #1.1 - Svc.run()\n"
                + "      #1.1.1 - Y.first()\n"
                + "    #1.2 - Svc.run()\n"
                + "      #1.2.1 - Y.second()\n");
  }

  @Test
  void aCycleLineCitesTheIdOfTheNodeItStopsAt() {
    var aChildren = new ArrayList<TraceNode>();
    var a = new TraceNode(sig("Recursive", "a"), aChildren, new TraceOutcome.Returned(null), 0L);
    var b = new TraceNode(sig("Recursive", "b"), List.of(a), new TraceOutcome.Returned(null), 0L);
    aChildren.add(b);

    assertThat(renderer.render(new DefaultTraceTree(List.of(a))))
        .isEqualTo(
            "#1 - Recursive.a()\n"
                + "  #1.1 - Recursive.b()\n"
                + "    #1.1.1 - Recursive.a() … (cycle)\n");
  }

  @Test
  void aFireAndForgetWorkRootKeepsItsOwnLineNotOnlyItsMarker() {
    var faf = new ConcurrencyInfo("fanf-1", "bg-1", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    var work = node("NotifySvc", "send", List.of(), faf);

    // SUSPECTED BUG: a worker root tagged FIRE_AND_FORGET with a real outcome is planned as if it
    // were the launcher node, so its own line is replaced by "~ fire-and-forget" and it is lost.
    assertThat(renderer.render(new DefaultTraceTree(List.of(work)))).contains("NotifySvc.send()");
  }

  @Test
  void everyWorkRootOfOneFireAndForgetGroupIsRendered() {
    var faf = new ConcurrencyInfo("fanf-1", "bg-1", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    var notify = node("NotifySvc", "send", List.of(), faf);
    var audit = node("AuditSvc", "record", List.of(), faf);

    // SUSPECTED BUG: the two roots share one fire-and-forget segment, which planSegment plans from
    // its first member only; the second root is dropped from the artifact without a trace.
    var result = renderer.render(new DefaultTraceTree(List.of(notify, audit)));

    assertThat(result).contains("NotifySvc.send()").contains("AuditSvc.record()");
  }

  @Test
  void aScenarioNameWithALineSeparatorIsEscapedInTheHeader() {
    var root = node("Svc", "run", List.of(), null);

    assertThat(renderer.renderDocument(new DefaultTraceTree(List.of(root)), "a b"))
        .startsWith("scenario: a\\u2028b\n\n");
  }

  private static TraceNode node(
      String type, String method, List<TraceNode> children, ConcurrencyInfo info) {
    return new TraceNode(
        sig(type, method), children, new TraceOutcome.Returned(null), 0L, 0L, info);
  }

  private static TraceNode leaf(String type, String method) {
    return node(type, method, List.of(), null);
  }

  private static MethodSignature sig(String type, String method) {
    return new MethodSignature(type, method, List.of());
  }
}
