/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link LoopFold#valueKey} recurses over a folded iteration's children to decide whether it is
 * value-identical to the run's first iteration — a comparison that has to survive the same hostile
 * shapes {@code MarkdownRenderer} itself must.
 */
class LoopFoldTest {

  private static TraceNode node(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepFoldedIterationIsComparedWithoutStackOverflow() {
    TraceNode firstChild = node("leaf", List.of());
    TraceNode foldedChild = node("leaf", List.of());
    for (var i = 0; i < 5_000; i++) {
      firstChild = node("call" + i, List.of(firstChild));
      foldedChild = node("call" + i, List.of(foldedChild));
    }
    var first = node("root", List.of(firstChild));
    var folded = node("root", List.of(foldedChild));
    var refs = ValueReferenceIndex.build(new DefaultTraceTree(List.of(first)));

    var summary = LoopFold.summaryLine(first, List.of(folded), refs);

    assertThat(summary).contains("×1 more");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicFoldedIterationIsComparedWithoutHanging() {
    var firstHolder = new ArrayList<TraceNode>();
    var firstB = node("b", firstHolder);
    var first = node("a", List.of(firstB));
    firstHolder.add(first);

    var foldedHolder = new ArrayList<TraceNode>();
    var foldedB = node("b", foldedHolder);
    var folded = node("a", List.of(foldedB));
    foldedHolder.add(folded);

    var refs = ValueReferenceIndex.build(new DefaultTraceTree(List.of(first)));

    var summary = LoopFold.summaryLine(first, List.of(folded), refs);

    assertThat(summary).contains("×1 more");
  }
}
