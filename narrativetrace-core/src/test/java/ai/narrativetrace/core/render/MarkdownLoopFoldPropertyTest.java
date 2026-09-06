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
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Invariants of loop folding that example tests cannot exhaust: whatever the run length or the mix
 * of shapes, folding compresses proven sameness without ever hiding a distinct shape.
 */
class MarkdownLoopFoldPropertyTest {

  private TraceNode leaf(String method, String value) {
    return new TraceNode(
        new MethodSignature("Svc", method, List.of(new ParameterCapture("v", value, false))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""));
  }

  private String render(List<TraceNode> children) {
    var parent =
        new TraceNode(
            new MethodSignature("Root", "run", List.of()),
            children,
            new TraceOutcome.Returned(null));
    return new MarkdownRenderer().render(new DefaultTraceTree(List.of(parent)));
  }

  @Property
  void aRunOfNIdenticalShapesRendersTheShapeOnceAndFoldsTheRest(
      @ForAll @IntRange(min = 2, max = 30) int n) {
    var children = new ArrayList<TraceNode>();
    for (var i = 0; i < n; i++) {
      children.add(leaf("step", "\"value-" + i + "\""));
    }

    var result = render(children);

    // The shape's full entry line renders exactly once (the first iteration)...
    assertThat(result.split("\\*\\*Svc.step\\*\\*")).hasSize(2);
    // ...and the remaining n-1 collapse into a single fold line naming that count.
    assertThat(result).contains("×" + (n - 1) + " more");
  }

  @Property
  void foldingNeverHidesAnyDistinctShape(
      @ForAll @Size(min = 1, max = 40) List<@IntRange(min = 0, max = 4) Integer> shapeIndexes) {
    var children = new ArrayList<TraceNode>();
    for (var index : shapeIndexes) {
      children.add(leaf("shape" + index, "\"x\""));
    }

    var result = render(children);

    // Every distinct method name that appeared among the siblings still appears in the output;
    // folding collapses repeats but never removes a shape wholesale.
    for (var index : shapeIndexes.stream().distinct().toList()) {
      assertThat(result).contains("**Svc.shape" + index + "**");
    }
  }
}
