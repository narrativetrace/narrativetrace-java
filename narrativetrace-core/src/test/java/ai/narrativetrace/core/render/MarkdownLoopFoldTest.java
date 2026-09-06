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
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Loop folding in the Markdown renderer: a maximal run of ≥2 consecutive same-shape sequential
 * sibling subtrees renders the first iteration in full and the remaining k as a single {@code ×k
 * more …} summary line. Compress proven sameness, amplify difference — a diverging sibling ends the
 * run and renders in full.
 */
class MarkdownLoopFoldTest {

  private TraceNode leaf(String className, String method, String paramName, String value) {
    return new TraceNode(
        new MethodSignature(
            className, method, List.of(new ParameterCapture(paramName, value, false))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        0L);
  }

  private TraceNode parentOf(List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Trip", "settle", List.of()),
        children,
        new TraceOutcome.Returned(null));
  }

  private String render(List<TraceNode> children) {
    return new MarkdownRenderer().render(new DefaultTraceTree(List.of(parentOf(children))));
  }

  private TraceNode expenseVal(String label, String amount) {
    return expense("Expense(description: \"" + label + "\", amount: " + amount + ")", label);
  }

  /** A {@code recordExpense(expense: …)} iteration with a validate ✓ → record ✓ child flow. */
  private TraceNode expense(String rendered, String description) {
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", java.util.Map.of("description", new RenderedValue.StringVal(description)));
    var validate =
        new TraceNode(
            new MethodSignature("Validator", "validate", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            2_000_000L);
    var record =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            3_000_000L);
    return new TraceNode(
        new MethodSignature(
            "Service",
            "recordExpense",
            List.of(new ParameterCapture("expense", rendered, false, structured))),
        List.of(validate, record),
        new TraceOutcome.Returned(null),
        5_000_000L);
  }

  @Test
  void foldedIterationsAreLabelledByDistinguishingArgumentWithFlowTail() {
    var result =
        render(
            List.of(
                expenseVal("Dinner", "100.00"),
                expenseVal("Taxi", "20.00"),
                expenseVal("Groceries", "55.00")));

    assertThat(result).contains("recordExpense**(expense: `Expense(description: \"Dinner\"");
    assertThat(result)
        .contains("- ×2 more: ‹Taxi›, ‹Groceries› — same flow (validate ✓ → record ✓)");
    // Only the first iteration's children render; Taxi/Groceries subtrees are folded away.
    assertThat(result.split("Validator.validate")).hasSize(2);
  }

  private TraceNode leafMs(String value, long millis) {
    return new TraceNode(
        new MethodSignature(
            "Ledger", "record", List.of(new ParameterCapture("note", value, false))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        millis * 1_000_000L);
  }

  @Test
  void foldLineAggregatesDurationsAsTotalAndRangeNeverPerIteration() {
    var result = render(List.of(leafMs("\"a\"", 4), leafMs("\"b\"", 2), leafMs("\"c\"", 6)));

    assertThat(result).contains("— 8ms total, 2–6ms each");
  }

  @Test
  void foldLineUsesEachWhenAllFoldedDurationsAreEqual() {
    var result = render(List.of(leafMs("\"a\"", 3), leafMs("\"b\"", 3)));

    assertThat(result).contains("— 3ms total, 3ms each");
  }

  @Test
  void foldLineOmitsDurationSegmentWhenNoTimingCaptured() {
    var result = render(List.of(leafMs("\"a\"", 0), leafMs("\"b\"", 0)));

    assertThat(result).doesNotContain("total");
    assertThat(result).contains("×1 more");
  }

  @Test
  void structurallyDivergingSiblingEndsRunAndDoesNotFoldAcrossIt() {
    // A A B A A where B is a *different call* (structural divergence, not just a different value):
    // two runs of A fold independently; B renders in full between them.
    var b =
        new TraceNode(
            new MethodSignature("Ledger", "reconcile", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var result =
        render(
            List.of(
                leafMs("\"a\"", 0), leafMs("\"a\"", 0), b, leafMs("\"a\"", 0), leafMs("\"a\"", 0)));

    assertThat(result.split("×1 more")).hasSize(3); // two separate fold lines
    assertThat(result).contains("Ledger.reconcile**()"); // B rendered fully, unfolded
  }

  @Test
  void siblingsDifferingOnlyByValueFoldTogetherWithLabels() {
    // Compress proven (structural) sameness, amplify difference: same shape, different value → one
    // run; the differing bare-string value has no identity object, so it takes a positional marker.
    var result = render(List.of(leafMs("\"a\"", 0), leafMs("\"b\"", 0), leafMs("\"a\"", 0)));

    assertThat(result)
        .contains("×2 more: #2"); // b (iteration #2) distinguished; the trailing a is identical
  }

  @Test
  void runOfTwoByteIdenticalSiblingsFoldsSecondIntoIdenticalSummary() {
    var a = leaf("Ledger", "record", "note", "\"x\"");
    var b = leaf("Ledger", "record", "note", "\"x\"");

    var result = render(List.of(a, b));

    assertThat(result).contains("  - **Ledger.record**(note: `\"x\"`) → `\"ok\"`");
    assertThat(result).contains("  - ×1 more (identical)");
    assertThat(result).doesNotContain("same flow"); // a childless run has no flow tail
    // The folded sibling's subtree is not rendered a second time.
    assertThat(result.split("Ledger.record")).hasSize(2);
  }

  private TraceNode memberLeaf(String rendered, String id) {
    return new TraceNode(
        new MethodSignature(
            "Svc",
            "pay",
            List.of(
                new ParameterCapture(
                    "member",
                    rendered,
                    false,
                    new RenderedValue.ObjectVal(
                        "Member", java.util.Map.of("id", new RenderedValue.StringVal(id)))))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""));
  }

  @Test
  void foldDisplayReturnsNullForNullRenderedValue() {
    var index =
        ValueReferenceIndex.build(new DefaultTraceTree(List.of(leaf("A", "m", "p", "\"x\""))));
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", java.util.Map.of("description", new RenderedValue.StringVal("Hotel")));

    assertThat(index.foldDisplay(null, structured)).isNull();
  }

  @Test
  void aRepeatedDistinguishingValueReusesItsFoldLabel() {
    var result =
        render(
            List.of(
                memberLeaf("Member(id: m-1)", "m-1"),
                memberLeaf("Member(id: m-9)", "m-9"),
                memberLeaf("Member(id: m-9)", "m-9")));

    assertThat(result).contains("×2 more: ‹m-9›, ‹m-9›");
  }

  private TraceNode rootWithChild(TraceNode child) {
    return new TraceNode(
        new MethodSignature("Svc", "run", List.of()),
        List.of(child),
        new TraceOutcome.Returned(null));
  }

  @Test
  void iterationsIdenticalAtRootButDifferingDeeperTakeAPositionalLabel() {
    java.util.function.Function<String, TraceNode> iter =
        v ->
            rootWithChild(
                new TraceNode(
                    new MethodSignature(
                        "Inner", "step", List.of(new ParameterCapture("v", v, false))),
                    List.of(),
                    new TraceOutcome.Returned("\"ok\"")));

    var result = render(List.of(iter.apply("\"x\""), iter.apply("\"y\"")));

    assertThat(result).contains("×1 more: #2 — same flow (step ✓)");
  }

  @Test
  void iterationsDifferingOnlyByReturnValueTakeAPositionalLabel() {
    var a =
        new TraceNode(
            new MethodSignature("Calc", "calc", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"a\""));
    var b =
        new TraceNode(
            new MethodSignature("Calc", "calc", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"b\""));

    var result = render(List.of(a, b));

    // Same shape, same (absent) params, only the return value differs → not "(identical)".
    assertThat(result).contains("×1 more: #2");
  }

  @Test
  void throwingChildShowsBangInFlowAndDiffersByMessage() {
    java.util.function.Function<String, TraceNode> iter =
        msg ->
            rootWithChild(
                new TraceNode(
                    new MethodSignature("Inner", "step", List.of()),
                    List.of(),
                    new TraceOutcome.Threw(new IllegalStateException(msg))));

    var result = render(List.of(iter.apply("first"), iter.apply("second")));

    assertThat(result).contains("×1 more: #2 — same flow (step !)");
  }

  @Test
  void incompleteChildShowsQuestionMarkInFlow() {
    java.util.function.Supplier<TraceNode> iter =
        () ->
            rootWithChild(
                new TraceNode(
                    new MethodSignature("Inner", "step", List.of()),
                    List.of(),
                    new TraceOutcome.Incomplete()));

    var result = render(List.of(iter.get(), iter.get()));

    assertThat(result).contains("×1 more (identical) — same flow (step ?)");
  }

  @Test
  void aSingleThrowingIterationAmongReturnsIsExcludedFromTheFoldAndRendersInFull() {
    var threw =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("boom")));
    var result = render(List.of(leafMs("\"a\"", 0), leafMs("\"a\"", 0), threw));

    assertThat(result).contains("×1 more"); // the two returning iterations fold
    assertThat(result).contains("❌ `IllegalStateException`: boom"); // the anomaly renders in full
  }

  @Test
  void iterationsThatAllThrowTheSameWayFoldTogether() {
    var t1 =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("nope")));
    var t2 =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("nope")));

    var result = render(List.of(t1, t2));

    assertThat(result).contains("×1 more (identical)");
    assertThat(result.split("IllegalStateException")).hasSize(2); // exception rendered once
  }

  @Test
  void outcomeKindMismatchReturnedVersusIncompleteDoesNotFold() {
    var returned =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var incomplete =
        new TraceNode(
            new MethodSignature("Ledger", "record", List.of()),
            List.of(),
            new TraceOutcome.Incomplete());

    var result = render(List.of(returned, incomplete));

    assertThat(result).doesNotContain("×");
    assertThat(result).contains("⏳ in-flight");
  }

  private TraceNode rootWithGrandchild(String grandchildMethod) {
    var grandchild =
        new TraceNode(
            new MethodSignature("Deep", grandchildMethod, List.of()),
            List.of(),
            new TraceOutcome.Returned("\"g\""));
    var child =
        new TraceNode(
            new MethodSignature("Mid", "step", List.of()),
            List.of(grandchild),
            new TraceOutcome.Returned(null));
    return new TraceNode(
        new MethodSignature("Top", "run", List.of()),
        List.of(child),
        new TraceOutcome.Returned(null));
  }

  @Test
  void deepNearMissWithDifferentGrandchildDoesNotFold() {
    var result = render(List.of(rootWithGrandchild("alpha"), rootWithGrandchild("beta")));

    assertThat(result).doesNotContain("×");
    assertThat(result).contains("Deep.alpha");
    assertThat(result).contains("Deep.beta");
  }

  @Test
  void nestedLoopsFoldIndependentlyAtEachLevel() {
    java.util.function.Supplier<TraceNode> outer =
        () ->
            new TraceNode(
                new MethodSignature("Batch", "process", List.of()),
                List.of(
                    leaf("Item", "handle", "id", "\"1\""),
                    leaf("Item", "handle", "id", "\"1\""),
                    leaf("Item", "handle", "id", "\"1\"")),
                new TraceOutcome.Returned(null));

    var result = render(List.of(outer.get(), outer.get()));

    assertThat(result).contains("×2 more"); // inner run of 3 folds inside the first outer
    assertThat(result).contains("×1 more"); // the two outer iterations fold
    // Only the first outer, and within it only the first inner item, render fully.
    assertThat(result.split("Item.handle")).hasSize(2);
  }

  @Test
  void foldLabelReusedByLaterReferenceStaysConsistent() {
    var dinner = "Expense(description: \"Dinner\", amount: 100.00)";
    var taxi = "Expense(description: \"Taxi\", amount: 20.00)";
    var report =
        new TraceNode(
            new MethodSignature(
                "Report",
                "summarize",
                List.of(
                    new ParameterCapture(
                        "expense",
                        taxi,
                        false,
                        new RenderedValue.ObjectVal(
                            "Expense",
                            java.util.Map.of(
                                "description", new RenderedValue.StringVal("Taxi")))))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var result = render(List.of(expense(dinner, "Dinner"), expense(taxi, "Taxi"), report));

    assertThat(result).contains("×1 more: ‹Taxi›"); // fold line names the folded iteration
    assertThat(result)
        .contains("summarize**(expense: `‹Taxi›`)"); // later use reuses the same label
  }

  @Test
  void narrationOfFoldedIterationsIsNotDuplicated() {
    java.util.function.Supplier<TraceNode> narr =
        () ->
            new TraceNode(
                new MethodSignature("Svc", "step", List.of(), "Doing the thing", null),
                List.of(),
                new TraceOutcome.Returned("\"ok\""));

    var result = render(List.of(narr.get(), narr.get()));

    assertThat(result.split("Doing the thing")).hasSize(2); // narration rendered once, on the first
    assertThat(result).contains("×1 more");
  }

  @Test
  void foldingWorksWithInlineParentReturns() {
    java.util.function.Supplier<TraceNode> iter =
        () ->
            new TraceNode(
                new MethodSignature("Svc", "handle", List.of()),
                List.of(
                    new TraceNode(
                        new MethodSignature("Inner", "call", List.of()),
                        List.of(),
                        new TraceOutcome.Returned("\"x\""))),
                new TraceOutcome.Returned("\"receipt\""));

    var result = render(List.of(iter.get(), iter.get()));

    assertThat(result).contains("handle**() → `\"receipt\"`"); // first parent's inline return
    assertThat(result).contains("×1 more (identical) — same flow (call ✓)");
  }

  @Test
  void foldingWorksWithSuppressedParameters() {
    java.util.function.Supplier<TraceNode> iter =
        () ->
            new TraceNode(
                new MethodSignature(
                    "Svc",
                    "run",
                    List.of(
                        new ParameterCapture("tripName", "", false).withoutValues(),
                        new ParameterCapture("id", "\"7\"", false))),
                List.of(),
                new TraceOutcome.Returned("\"ok\""));

    var result = render(List.of(iter.get(), iter.get()));

    assertThat(result).contains("run**(tripName: …, id: `\"7\"`)"); // first shows the ellipsis
    assertThat(result).contains("×1 more (identical)");
  }

  @Test
  void labelNeverComesFromARedactedIdentityField() {
    java.util.function.BiFunction<String, String, TraceNode> member =
        (rendered, id) ->
            new TraceNode(
                new MethodSignature(
                    "Svc",
                    "pay",
                    List.of(
                        new ParameterCapture(
                            "member",
                            rendered,
                            false,
                            new RenderedValue.ObjectVal(
                                "Member",
                                new java.util.LinkedHashMap<>(
                                    java.util.Map.of(
                                        "name", new RenderedValue.StringVal("[REDACTED]"),
                                        "id", new RenderedValue.StringVal(id))))))),
                List.of(),
                new TraceOutcome.Returned("\"ok\""));

    var result =
        render(
            List.of(
                member.apply("Member(name: [REDACTED], id: m-17)", "m-17"),
                member.apply("Member(name: [REDACTED], id: m-18)", "m-18")));

    assertThat(result).contains("×1 more: ‹m-18›");
    assertThat(result).doesNotContain("‹[REDACTED]›");
  }

  @Test
  void topLevelRootsFoldToo() {
    var result =
        new MarkdownRenderer()
            .render(
                new DefaultTraceTree(
                    List.of(leaf("Job", "run", "n", "\"1\""), leaf("Job", "run", "n", "\"1\""))));

    assertThat(result).contains("- **Job.run**(n: `\"1\"`)");
    assertThat(result).contains("- ×1 more (identical)");
  }

  @Test
  void labelListIsCappedWithAnEllipsis() {
    var iterations = new java.util.ArrayList<TraceNode>();
    for (var i = 1; i <= 9; i++) {
      var id = "m-" + i;
      iterations.add(
          new TraceNode(
              new MethodSignature(
                  "Svc",
                  "pay",
                  List.of(
                      new ParameterCapture(
                          "member",
                          "Member(id: " + id + ")",
                          false,
                          new RenderedValue.ObjectVal(
                              "Member", java.util.Map.of("id", new RenderedValue.StringVal(id)))))),
              List.of(),
              new TraceOutcome.Returned("\"ok\"")));
    }

    var result = render(iterations);

    assertThat(result).contains("‹m-2›, ‹m-3›, ‹m-4›, ‹m-5›, ‹m-6›, ‹m-7›, …");
    assertThat(result).doesNotContain("‹m-8›");
  }

  @Test
  void flowTailIsCappedWithAnEllipsis() {
    java.util.function.Supplier<TraceNode> iter =
        () -> {
          var children = new java.util.ArrayList<TraceNode>();
          for (var i = 1; i <= 10; i++) {
            children.add(
                new TraceNode(
                    new MethodSignature("C", "m" + i, List.of()),
                    List.of(),
                    new TraceOutcome.Returned("\"ok\"")));
          }
          return new TraceNode(
              new MethodSignature("Svc", "run", List.of()),
              children,
              new TraceOutcome.Returned(null));
        };

    var result = render(List.of(iter.get(), iter.get()));

    assertThat(result)
        .contains("same flow (m1 ✓ → m2 ✓ → m3 ✓ → m4 ✓ → m5 ✓ → m6 ✓ → m7 ✓ → m8 ✓ → …)");
    assertThat(result).doesNotContain("m9 ✓");
  }
}
