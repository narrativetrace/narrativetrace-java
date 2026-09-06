/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link TraceTree#durationNanos()} is the contract's single answer to "how long did this take",
 * and it is a default method — so it is the API's behaviour to defend, not the runtime's.
 */
class TraceTreeDurationTest {

  @Test
  void anEmptyTreeTookNoTime() {
    assertThat(treeOf().durationNanos()).isZero();
  }

  @Test
  void oneTimedRootSpansItsOwnWindow() {
    assertThat(treeOf(node("root", 100L, 40L)).durationNanos()).isEqualTo(40L);
  }

  @Test
  void twoRootsSpanFromTheFirstStartToTheLastEnd() {
    var tree = treeOf(node("first", 100L, 10L), node("second", 200L, 30L));

    assertThat(tree.durationNanos())
        .as("a gap between two roots is time a wall clock would show")
        .isEqualTo(130L);
  }

  @Test
  void overlappingConcurrentRootsAreCountedOnce() {
    var tree = treeOf(node("a", 100L, 100L), node("b", 120L, 40L));

    assertThat(tree.durationNanos()).isEqualTo(100L);
  }

  @Test
  void anAdoptedChildThatOutlivesItsRootExtendsTheSpan() {
    var late = node("late", 300L, 50L);
    var root = node("root", 100L, 20L, late);

    assertThat(treeOf(root).durationNanos())
        .as("computed over all nodes, not roots alone")
        .isEqualTo(250L);
  }

  @Test
  void aTreeWithNoTimedNodeFallsBackToItsLongestRoot() {
    var tree = treeOf(untimed("short", 5L), untimed("long", 40L));

    assertThat(tree.durationNanos())
        .as("a hand-built tree carries no start times; the longest root is the tightest bound")
        .isEqualTo(40L);
  }

  @Test
  void untimedNodesAreSkippedWhenAnyNodeIsTimed() {
    var tree = treeOf(untimed("hand-built", 999L), node("captured", 100L, 10L));

    assertThat(tree.durationNanos()).isEqualTo(10L);
  }

  /**
   * A cyclic tree (a node whose own children list, still reachable, contains an ancestor) must
   * terminate instead of growing {@code pending} forever — {@code TraceNode.children()} is an
   * undefended list, so nothing but this method stops a hand-built or replayed tree from being
   * cyclic. {@code @Timeout} makes a regression a fast failure instead of a hung gate.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicTreeComputesADurationInsteadOfLoopingForever() {
    var bChildren = new ArrayList<TraceNode>();
    var b = node("b", 200L, 10L, bChildren);
    var a = node("a", 100L, 10L, List.of(b));
    bChildren.add(a);

    assertThat(treeOf(a).durationNanos()).isEqualTo(110L);
  }

  /**
   * A large ring (no repeat within a small traversal prefix) must not slip through as though it
   * were merely a legitimate width — the visited guard must catch a repeat at any distance.
   *
   * <p>Start times begin at {@code 10}, not {@code 0}: {@code durationNanos()} treats a node whose
   * own {@code startTimeNanos() == 0L} as untimed (a hand-built node with no captured start), which
   * would silently drop n0 from the span instead of exercising the cycle guard this test targets.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aHundredNodeRingComputesADurationInsteadOfLoopingForever() {
    var nodes = new ArrayList<TraceNode>();
    var childLists = new ArrayList<ArrayList<TraceNode>>();
    for (var i = 0; i < 100; i++) {
      var children = new ArrayList<TraceNode>();
      childLists.add(children);
      nodes.add(node("n" + i, (i + 1) * 10L, 5L, children));
    }
    for (var i = 0; i < 100; i++) {
      childLists.get(i).add(nodes.get((i + 1) % 100));
    }

    assertThat(treeOf(nodes.get(0)).durationNanos())
        .as("earliest start (n0, t=10) to latest end (n99, t=1000+5)")
        .isEqualTo(995L);
  }

  private static TraceNode node(String name, long start, long duration, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Svc", name, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        duration,
        start,
        null);
  }

  private static TraceNode node(String name, long start, long duration, TraceNode... children) {
    return new TraceNode(
        new MethodSignature("Svc", name, List.of()),
        List.of(children),
        new TraceOutcome.Returned("\"ok\""),
        duration,
        start,
        null);
  }

  private static TraceNode untimed(String name, long duration) {
    return new TraceNode(
        new MethodSignature("Svc", name, List.of()),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        duration);
  }

  private static TraceTree treeOf(TraceNode... roots) {
    var rootList = List.of(roots);
    return new TraceTree() {
      @Override
      public List<TraceNode> roots() {
        return rootList;
      }

      @Override
      public boolean isEmpty() {
        return rootList.isEmpty();
      }
    };
  }
}
