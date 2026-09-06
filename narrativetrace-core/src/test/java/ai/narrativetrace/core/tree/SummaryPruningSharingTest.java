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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What SUMMARY pruning rebuilds, and what it hands back untouched.
 *
 * <p>INTENT: SUMMARY keeps the root and the leaves-and-failures beneath it, dropping the
 * intermediate levels. A root whose children are already exactly that set is not pruned at all, and
 * a capture must not pay to rebuild it: the node, and the list holding it, come back as the same
 * instances. {@link TraceNode} is an immutable record, so identity carries no meaning a caller
 * could depend on — which is what makes the sharing safe as well as cheap.
 */
class SummaryPruningSharingTest {

  private static final MethodSignature SIG_A =
      new MethodSignature("ServiceA", "methodA", List.of());
  private static final MethodSignature SIG_B =
      new MethodSignature("ServiceB", "methodB", List.of());
  private static final MethodSignature SIG_C =
      new MethodSignature("ServiceC", "methodC", List.of());

  private static TraceNode returned(MethodSignature signature, TraceNode... children) {
    return new TraceNode(signature, List.of(children), new TraceOutcome.Returned("\"ok\""));
  }

  private static TraceNode threw(MethodSignature signature, TraceNode... children) {
    return new TraceNode(
        signature, List.of(children), new TraceOutcome.Threw(new IllegalStateException("no")));
  }

  @Test
  void aRootWhoseChildrenAreAllLeavesIsTheVeryNodeItWasGiven() {
    var root = returned(SIG_A, returned(SIG_B), returned(SIG_C));

    assertThat(TraceTreeBuilder.pruneSummary(List.of(root))).first().isSameAs(root);
  }

  @Test
  void aChildlessRootIsTheVeryNodeItWasGiven() {
    var root = returned(SIG_A);

    assertThat(TraceTreeBuilder.pruneSummary(List.of(root))).first().isSameAs(root);
  }

  @Test
  void aFailedChildKeepsItsOwnSubtreeSoTheRootAboveItIsUnchanged() {
    var root = returned(SIG_A, threw(SIG_B, returned(SIG_C)));

    assertThat(TraceTreeBuilder.pruneSummary(List.of(root))).first().isSameAs(root);
  }

  @Test
  void theRootListItselfIsSharedWhenNoRootChanged() {
    var roots = List.of(returned(SIG_A, returned(SIG_B)), returned(SIG_C));

    assertThat(TraceTreeBuilder.pruneSummary(roots)).isSameAs(roots);
  }

  @Test
  void aRootThatLiftsAGrandchildIsRebuiltAroundTheOriginalGrandchild() {
    var grandchild = returned(SIG_C);
    var root = returned(SIG_A, returned(SIG_B, grandchild));

    var pruned = TraceTreeBuilder.pruneSummary(List.of(root));

    assertThat(pruned).first().isNotSameAs(root);
    assertThat(pruned.get(0).children()).containsExactly(grandchild);
    assertThat(pruned.get(0).children().get(0)).isSameAs(grandchild);
  }

  @Test
  void onlyTheRootThatChangedIsRebuiltWhenSiblingsAreUntouched() {
    var untouched = returned(SIG_A, returned(SIG_B));
    var grandchild = returned(SIG_C);
    var lifted = returned(SIG_A, returned(SIG_B, grandchild));

    var pruned = TraceTreeBuilder.pruneSummary(List.of(untouched, lifted));

    assertThat(pruned.get(0)).isSameAs(untouched);
    assertThat(pruned.get(1)).isNotSameAs(lifted);
    assertThat(pruned.get(1).children()).containsExactly(grandchild);
  }
}
