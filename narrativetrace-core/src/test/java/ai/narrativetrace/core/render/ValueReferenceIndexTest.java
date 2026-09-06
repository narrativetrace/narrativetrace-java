/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link ValueReferenceIndex#build} walks the whole tree collecting candidate values before {@code
 * MarkdownRenderer} renders a byte of it — the bound has to hold here independently of the renderer
 * that calls it.
 */
class ValueReferenceIndexTest {

  private static TraceNode node(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(
            "Recursive", methodName, List.of(new ParameterCapture("value", "\"x\"", false))),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepTreeIsIndexedWithoutStackOverflow() {
    TraceNode current = node("call5000", List.of());
    for (var i = 0; i < 5_000; i++) {
      current = node("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    assertThatCode(() -> ValueReferenceIndex.build(tree)).doesNotThrowAnyException();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicTreeIsIndexedWithoutHanging() {
    var childHolder = new ArrayList<TraceNode>();
    var b = node("b", childHolder);
    var a = node("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var index = ValueReferenceIndex.build(tree);

    assertThat(index).isNotNull();
  }
}
