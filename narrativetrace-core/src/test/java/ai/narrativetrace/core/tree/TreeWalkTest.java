/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TreeWalkTest {

  /** A minimal mutable node — mutable so a test can wire up a genuine reference cycle. */
  static final class Node {
    final String name;
    final List<Node> children = new ArrayList<>();

    Node(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static List<Node> childrenOf(Node node) {
    return node.children;
  }

  @Test
  void visitsEveryNodeOfAShallowTreeInPreOrder() {
    var root = new Node("root");
    var left = new Node("left");
    var right = new Node("right");
    root.children.add(left);
    root.children.add(right);
    var visited = new ArrayList<String>();

    TreeWalk.walk(
        root,
        TreeWalkTest::childrenOf,
        (n, depth) -> visited.add(n.name + "@" + depth),
        (n, d, r) -> {});

    assertThat(visited).containsExactly("root@0", "left@1", "right@1");
  }

  @Test
  void aVeryDeepLegitimateChainRendersWholeWithoutStackOverflow() {
    // A real recursive business method can produce a deep, but not hostile, call tree. This must
    // not crash, and every node up to MAX_DEPTH must be visited normally.
    var root = new Node("n0");
    var current = root;
    var depth = 5_000;
    for (var i = 1; i <= depth; i++) {
      var child = new Node("n" + i);
      current.children.add(child);
      current = child;
    }
    var visitedCount = new int[1];
    var limitCount = new int[1];

    assertThatCode(
            () ->
                TreeWalk.walk(
                    root,
                    TreeWalkTest::childrenOf,
                    (n, d) -> visitedCount[0]++,
                    (n, d, r) -> limitCount[0]++))
        .doesNotThrowAnyException();

    assertThat(visitedCount[0]).isEqualTo(depth + 1);
    assertThat(limitCount[0]).isZero();
  }

  @Test
  void aChainDeeperThanMaxDepthStopsWithTheDepthLimitMarkerAndNeverCrashes() {
    var root = new Node("n0");
    var current = root;
    var depth = TreeWalk.MAX_DEPTH + 50;
    for (var i = 1; i <= depth; i++) {
      var child = new Node("n" + i);
      current.children.add(child);
      current = child;
    }
    var visitedCount = new int[1];
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(
        root, TreeWalkTest::childrenOf, (n, d) -> visitedCount[0]++, (n, d, r) -> limits.add(r));

    // Root is depth 0, so MAX_DEPTH + 1 nodes (0..MAX_DEPTH inclusive) are visited before the walk
    // stops descending.
    assertThat(visitedCount[0]).isEqualTo(TreeWalk.MAX_DEPTH + 1);
    assertThat(limits).containsExactly(TreeWalk.Reason.DEPTH_LIMIT);
  }

  @Test
  void depthLimitReasonMarkerTextIsStable() {
    assertThat(TreeWalk.Reason.DEPTH_LIMIT.marker()).isEqualTo("… (depth limit)");
    assertThat(TreeWalk.Reason.CYCLE.marker()).isEqualTo("… (cycle)");
  }

  @Test
  void aSelfHoldingNodeStopsWithTheCycleMarkerAndNeverHangs() {
    var root = new Node("root");
    root.children.add(root);
    var visitedCount = new int[1];
    var limits = new ArrayList<TreeWalk.Reason>();

    assertThatCode(
            () ->
                TreeWalk.walk(
                    root,
                    TreeWalkTest::childrenOf,
                    (n, d) -> visitedCount[0]++,
                    (n, d, r) -> limits.add(r)))
        .doesNotThrowAnyException();

    assertThat(visitedCount[0]).isEqualTo(1);
    assertThat(limits).containsExactly(TreeWalk.Reason.CYCLE);
  }

  @Test
  void aTwoNodeRingStopsWithTheCycleMarkerAndNeverHangs() {
    var a = new Node("a");
    var b = new Node("b");
    a.children.add(b);
    b.children.add(a);
    var visited = new ArrayList<String>();
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(
        a, TreeWalkTest::childrenOf, (n, d) -> visited.add(n.name), (n, d, r) -> limits.add(r));

    assertThat(visited).containsExactly("a", "b");
    assertThat(limits).containsExactly(TreeWalk.Reason.CYCLE);
  }

  @Test
  void aHundredNodeRingStopsWithoutHangingOrOverflowing() {
    var nodes = new ArrayList<Node>();
    for (var i = 0; i < 100; i++) {
      nodes.add(new Node("n" + i));
    }
    for (var i = 0; i < 100; i++) {
      nodes.get(i).children.add(nodes.get((i + 1) % 100));
    }
    var visitedCount = new int[1];
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(
        nodes.get(0),
        TreeWalkTest::childrenOf,
        (n, d) -> visitedCount[0]++,
        (n, d, r) -> limits.add(r));

    assertThat(visitedCount[0]).isEqualTo(100);
    assertThat(limits).containsExactly(TreeWalk.Reason.CYCLE);
  }

  @Test
  void aSharedSubtreeReachedByTwoBranchesIsNotFlaggedAsACycle() {
    // A diamond: the same object reached twice via non-overlapping paths is sharing, not a cycle.
    var shared = new Node("shared");
    var left = new Node("left");
    var right = new Node("right");
    var root = new Node("root");
    left.children.add(shared);
    right.children.add(shared);
    root.children.add(left);
    root.children.add(right);
    var visited = new ArrayList<String>();
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(
        root, TreeWalkTest::childrenOf, (n, d) -> visited.add(n.name), (n, d, r) -> limits.add(r));

    assertThat(visited).containsExactly("root", "left", "shared", "right", "shared");
    assertThat(limits).isEmpty();
  }

  @Test
  void aNodeReVisitedAfterLeavingThePathIsNotFlaggedAsACycle() {
    // Sequential siblings pointing at the same child (not nested) must not be mistaken for a cycle
    // either: by the time the second sibling is entered, the first has already left the ancestor
    // set.
    var repeated = new Node("repeated");
    var first = new Node("first");
    var second = new Node("second");
    first.children.add(repeated);
    second.children.add(repeated);
    var root = new Node("root");
    root.children.add(first);
    root.children.add(second);
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(root, TreeWalkTest::childrenOf, (n, d) -> {}, (n, d, r) -> limits.add(r));

    assertThat(limits).isEmpty();
  }

  @Test
  void leafNodeHasNoChildren() {
    var root = new Node("root");
    var visited = new ArrayList<String>();

    TreeWalk.walk(root, TreeWalkTest::childrenOf, (n, d) -> visited.add(n.name), (n, d, r) -> {});

    assertThat(visited).containsExactly("root");
  }

  @Test
  void enterAndExitOverloadCallsExitOncePerNodeAfterAllItsChildren() {
    var root = new Node("root");
    var left = new Node("left");
    var right = new Node("right");
    var leftChild = new Node("leftChild");
    root.children.add(left);
    root.children.add(right);
    left.children.add(leftChild);
    var events = new ArrayList<String>();

    TreeWalk.walk(
        root,
        TreeWalkTest::childrenOf,
        (n, d) -> events.add("enter:" + n.name),
        (n, d) -> events.add("exit:" + n.name),
        (n, d, r) -> events.add("limit:" + n.name));

    assertThat(events)
        .containsExactly(
            "enter:root",
            "enter:left",
            "enter:leftChild",
            "exit:leftChild",
            "exit:left",
            "enter:right",
            "exit:right",
            "exit:root");
  }

  @Test
  void enterAndExitOverloadNeverCallsExitForALimitedNode() {
    var a = new Node("a");
    var b = new Node("b");
    a.children.add(b);
    b.children.add(a);
    var exited = new ArrayList<String>();
    var limited = new ArrayList<String>();

    TreeWalk.walk(
        a,
        TreeWalkTest::childrenOf,
        (n, d) -> {},
        (n, d) -> exited.add(n.name),
        (n, d, r) -> limited.add(n.name));

    assertThat(exited).containsExactly("b", "a");
    assertThat(limited).containsExactly("a");
  }

  @Test
  void threeArgOverloadNeverInvokesAnExitCallback() {
    // Regression for the delegation from the pre-order-only overload to the enter/exit one: the
    // no-op exit visitor must never surface as a caller-visible callback.
    var root = new Node("root");
    root.children.add(new Node("child"));

    assertThatCode(
            () -> TreeWalk.walk(root, TreeWalkTest::childrenOf, (n, d) -> {}, (n, d, r) -> {}))
        .doesNotThrowAnyException();
  }

  @Test
  void identityCycleDetectionIsNotFooledByEqualButDistinctNodes() {
    // Two distinct node instances that happen to be equal (same content) must not be treated as a
    // cycle: cycle detection here is identity-based, not equals-based.
    var a = new EqualNode("x");
    var b = new EqualNode("x");
    assertThat(a).isEqualTo(b);
    a.children.add(b);
    var visited = new ArrayList<String>();
    var limits = new ArrayList<TreeWalk.Reason>();

    TreeWalk.walk(
        a, n -> n.children, (n, d) -> visited.add(n.name + "@" + d), (n, d, r) -> limits.add(r));

    assertThat(visited).containsExactly("x@0", "x@1");
    assertThat(limits).isEmpty();
  }

  /** A node type with value equality, to prove cycle detection ignores {@code equals}. */
  static final class EqualNode {
    final String name;
    final List<EqualNode> children = new ArrayList<>();

    EqualNode(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualNode e && name.equals(e.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  @Test
  void childrenOfFunctionIsConsultedExactlyOncePerVisitedNode() {
    var callCount = new AtomicInteger();
    var root = new Node("root");
    root.children.add(new Node("a"));
    root.children.add(new Node("b"));

    TreeWalk.walk(
        root,
        node -> {
          callCount.incrementAndGet();
          return node.children;
        },
        (n, d) -> {},
        (n, d, r) -> {});

    assertThat(callCount.get()).isEqualTo(3);
  }
}
