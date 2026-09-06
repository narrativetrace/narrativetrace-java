/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A scenario's duration is the wall-clock span from its first entry to its last exit — the
 * definition {@code chapter.schema.json} has always declared. Summing is wrong at every level: a
 * span's duration already contains its children's, so adding siblings double-counts against their
 * parent, and adding roots repeats the mistake one level up.
 */
class ScenarioDurationTest {

  private static final long MS = 1_000_000L;

  @Test
  void aSingleRootReportsItsOwnSpan() {
    var tree = treeOf(node("Order", "place", 100 * MS, 11 * MS));

    assertThat(tree.durationNanos()).isEqualTo(11 * MS);
  }

  @Test
  void twoRootsSpanFromTheFirstEntryToTheLastExit() {
    // The item-29 case: async work submitted after its caller returned is a second root.
    var tree =
        treeOf(
            node("Order", "place", 100 * MS, 11 * MS),
            node("Notifier", "notify", 112 * MS, 298 * MS));

    assertThat(tree.durationNanos()).isEqualTo(310 * MS);
  }

  @Test
  void overlappingRootsAreCountedOnceNotSummed() {
    var tree =
        treeOf(
            node("A", "one", 100 * MS, 200 * MS), // 100 → 300
            node("B", "two", 150 * MS, 100 * MS)); // 150 → 250, wholly inside A

    assertThat(tree.durationNanos()).isEqualTo(200 * MS);
  }

  @Test
  void aChildThatOutlivesItsRootExtendsTheSpan() {
    // Adoption lets an async child finish after the root that launched it exited.
    var root =
        new TraceNode(
            sig("Order", "place"),
            List.of(node("Notifier", "notify", 105 * MS, 400 * MS)),
            new TraceOutcome.Returned("value"),
            10 * MS,
            100 * MS,
            null,
            null,
            null);

    assertThat(treeOf(root).durationNanos()).isEqualTo(405 * MS);
  }

  @Test
  void aGapBetweenRootsCountsAsWallClock() {
    var tree = treeOf(node("A", "one", 100 * MS, 1 * MS), node("B", "two", 5_000 * MS, 1 * MS));

    assertThat(tree.durationNanos()).isEqualTo(4_901 * MS);
  }

  @Test
  void anEmptyTreeHasNoDuration() {
    assertThat(new DefaultTraceTree(List.of()).durationNanos()).isZero();
  }

  @Test
  void aTreeWithNoTimestampsFallsBackToItsLongestRoot() {
    // Hand-built trees carry durations but no start times, so a span cannot be derived; the longest
    // root is the tightest lower bound the data supports.
    var tree =
        treeOf(
            new TraceNode(sig("A", "one"), List.of(), new TraceOutcome.Returned("value"), 40 * MS),
            new TraceNode(sig("B", "two"), List.of(), new TraceOutcome.Returned("value"), 90 * MS));

    assertThat(tree.durationNanos()).isEqualTo(90 * MS);
  }

  @Test
  void nodesWithoutTimestampsOrDurationsContributeNothing() {
    // Hand-built trees (the three-argument constructor) carry no timing; a tree of them must not
    // read as having started at the epoch of the monotonic clock.
    var tree =
        treeOf(
            new TraceNode(sig("A", "one"), List.of(), new TraceOutcome.Returned("value")),
            new TraceNode(sig("B", "two"), List.of(), new TraceOutcome.Returned("value")));

    assertThat(tree.durationNanos()).isZero();
  }

  @Test
  void aMixOfTimedAndUntimedNodesUsesOnlyTheTimedOnes() {
    var tree =
        treeOf(
            new TraceNode(sig("Synthetic", "node"), List.of(), new TraceOutcome.Returned("value")),
            node("Real", "call", 900 * MS, 20 * MS));

    assertThat(tree.durationNanos()).isEqualTo(20 * MS);
  }

  private static TraceTree treeOf(TraceNode... roots) {
    return new DefaultTraceTree(List.of(roots));
  }

  private static TraceNode node(String className, String method, long startNanos, long durNanos) {
    return new TraceNode(
        sig(className, method),
        List.of(),
        new TraceOutcome.Returned("value"),
        durNanos,
        startNanos,
        null,
        null,
        null);
  }

  private static MethodSignature sig(String className, String method) {
    return new MethodSignature(className, method, List.of());
  }
}
