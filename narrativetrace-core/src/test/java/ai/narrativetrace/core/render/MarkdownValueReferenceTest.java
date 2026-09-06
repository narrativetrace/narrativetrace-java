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
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Content-addressed value deduplication in the markdown renderer: a captured value rendered
 * identically more than once is defined with a readable reference on first emission and referred to
 * by that reference afterwards. Byte equality certifies sameness; any difference renders in full.
 */
class MarkdownValueReferenceTest {

  private static final String HOTEL =
      "Expense(description: \"Hotel\", amount: 400.00, currency: \"EUR\")";

  private TraceNode leaf(String className, String method, String paramName, String value) {
    return new TraceNode(
        new MethodSignature(
            className, method, List.of(new ParameterCapture(paramName, value, false))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  private TraceNode leafWithStructured(
      String className, String method, String value, RenderedValue structured) {
    return new TraceNode(
        new MethodSignature(
            className, method, List.of(new ParameterCapture("expense", value, false, structured))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  @Test
  void labelComesFromIdentityFieldOfStructuredValue() {
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal("Hotel")));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("ExpenseValidator", "ensureValid", HOTEL, structured),
                leafWithStructured("TripLedger", "recordExpense", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("ensureValid**(expense: `‹Hotel›=" + HOTEL + "`)");
    assertThat(result).contains("recordExpense**(expense: `‹Hotel›`)");
  }

  @Test
  void referencedValueIsReplacedInsideContainerValues() {
    var listRender = "[" + HOTEL + "]";
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL),
                leaf("TripLedger", "recordExpense", "expense", HOTEL),
                new TraceNode(
                    new MethodSignature("TripLedger", "expensesOf", List.of()),
                    List.of(),
                    new TraceOutcome.Returned(listRender),
                    1_000_000L)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("expensesOf**() → `[‹v1›]`");
  }

  @Test
  void containmentInsideAnotherCapturedValueCountsTowardReference() {
    var listRender = "[" + HOTEL + "]";
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL),
                new TraceNode(
                    new MethodSignature("TripLedger", "expensesOf", List.of()),
                    List.of(),
                    new TraceOutcome.Returned(listRender),
                    1_000_000L)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("ensureValid**(expense: `‹v1›=" + HOTEL + "`)");
    assertThat(result).contains("expensesOf**() → `[‹v1›]`");
  }

  @Test
  void firstOccurrenceInsideContainerDefinesInlineAndLaterStandaloneUsesLabel() {
    var listRender = "[" + HOTEL + "]";
    var tree =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    new MethodSignature("TripLedger", "expensesOf", List.of()),
                    List.of(),
                    new TraceOutcome.Returned(listRender),
                    1_000_000L),
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("expensesOf**() → `[‹v1›=" + HOTEL + "]`");
    assertThat(result).contains("ensureValid**(expense: `‹v1›`)");
  }

  @Test
  void collidingIdentityLabelsAreDisambiguatedWithOrdinals() {
    var otherHotel = "Expense(description: \"Hotel\", amount: 999.99, currency: \"CHF\")";
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal("Hotel")));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured),
                leafWithStructured("B", "third", otherHotel, structured),
                leafWithStructured("B", "fourth", otherHotel, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("first**(expense: `‹Hotel›=" + HOTEL + "`)");
    assertThat(result).contains("third**(expense: `‹Hotel·2›=" + otherHotel + "`)");
    assertThat(result).contains("fourth**(expense: `‹Hotel·2›`)");
  }

  @Test
  void identityLabelIsCappedAtTwentyFourCharacters() {
    var longName = "A-very-long-hotel-description-beyond-cap";
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal(longName)));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("‹" + longName.substring(0, 24) + "…›");
  }

  @Test
  void redactedIdentityFieldIsSkippedForTheNextCandidate() {
    var fields = new java.util.LinkedHashMap<String, RenderedValue>();
    fields.put("name", new RenderedValue.StringVal("[REDACTED]"));
    fields.put("id", new RenderedValue.StringVal("m-17"));
    var structured = new RenderedValue.ObjectVal("Member", fields);
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("‹m-17›=" + HOTEL);
    assertThat(result).doesNotContain("‹[REDACTED]›");
  }

  @Test
  void objectWithoutUsableIdentityFieldFallsBackToTypeName() {
    var structured =
        new RenderedValue.ObjectVal("Expense", Map.of("amount", new RenderedValue.DoubleVal(1.0)));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("‹Expense›=" + HOTEL);
    assertThat(result).contains("first**(expense: `‹Expense›=");
  }

  @Test
  void controlCharactersInIdentityValueAreSanitizedInTheLabel() {
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal("Ho\ntel")));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("‹Ho\\ntel›");
  }

  @Test
  void shortRepeatedValuesAreNeverReferenced() {
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("A", "first", "tripName", "\"Ski Weekend\""),
                leaf("A", "second", "tripName", "\"Ski Weekend\"")));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("first**(tripName: `\"Ski Weekend\"`)");
    assertThat(result).contains("second**(tripName: `\"Ski Weekend\"`)");
    assertThat(result).doesNotContain("‹");
  }

  @Test
  void parentInlineReturnDefinesTheReferenceAndChildReturnReusesIt() {
    var transfers = "[Transfer(from: \"Carol\", to: \"Alice\", amount: 111.25)]";
    var child =
        new TraceNode(
            new MethodSignature("SettlementPlanner", "planTransfers", List.of()),
            List.of(),
            new TraceOutcome.Returned(transfers),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("TripSettlementService", "settleTrip", List.of()),
            List.of(child),
            new TraceOutcome.Returned(transfers),
            2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("settleTrip**() → `‹v1›=" + transfers + "` — 2ms");
    assertThat(result).contains("planTransfers**() → `‹v1›`");
    assertThat(result).doesNotContain("\n  - → ");
  }

  @Test
  void containmentWeightCountsEachEmissionOfTheContainer() {
    var listRender = "[" + HOTEL + "]";
    // Two distinct call sites returning the same container: the value string is emitted twice (so
    // containment weighting still counts each), but the differing method names keep them out of a
    // loop-fold run, so both emissions render and the reuse form is visible.
    var listNode =
        (java.util.function.Function<String, TraceNode>)
            method ->
                new TraceNode(
                    new MethodSignature("TripLedger", method, List.of()),
                    List.of(),
                    new TraceOutcome.Returned(listRender),
                    1_000_000L);
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL),
                listNode.apply("expensesOf"),
                listNode.apply("allExpenses")));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("ensureValid**(expense: `‹v1›=" + HOTEL + "`)");
    assertThat(result).contains("`‹v2›=[‹v1›]`");
    assertThat(result).contains("→ `‹v2›`");
  }

  @Test
  void valueAtIndexZeroOfContainerIsCountedAndReplaced() {
    var container = HOTEL + " recorded at 09:15";
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL),
                new TraceNode(
                    new MethodSignature("AuditLog", "lastEntry", List.of()),
                    List.of(),
                    new TraceOutcome.Returned(container),
                    1_000_000L)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("ensureValid**(expense: `‹v1›=" + HOTEL + "`)");
    assertThat(result).contains("lastEntry**() → `‹v1› recorded at 09:15`");
  }

  @Test
  void valueExactlyAtMinimumReferenceLengthIsReferenced() {
    var exactlyForty = "0123456789012345678901234567890123456789";
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("A", "first", "token", exactlyForty),
                leaf("A", "second", "token", exactlyForty)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("first**(token: `‹v1›=" + exactlyForty + "`)");
    assertThat(result).contains("second**(token: `‹v1›`)");
  }

  @Test
  void labelExactlyAtCapLengthIsNotTruncated() {
    var exactlyTwentyFour = "123456789012345678901234";
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal(exactlyTwentyFour)));
    var tree =
        new DefaultTraceTree(
            List.of(
                leafWithStructured("A", "first", HOTEL, structured),
                leafWithStructured("A", "second", HOTEL, structured)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("‹" + exactlyTwentyFour + "›=" + HOTEL);
    assertThat(result).doesNotContain("…›");
  }

  @Test
  void nullRenderedValueStaysNullInsteadOfEnteringReferenceMachinery() {
    var tree =
        new DefaultTraceTree(
            List.of(leaf("A", "first", "expense", HOTEL), leaf("A", "second", "expense", HOTEL)));
    var index = ValueReferenceIndex.build(tree);

    assertThat(index.display(null)).isNull();
  }

  @Test
  void redactedParameterRendersMarkerNotItsCapturedValue() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AccountService",
                "login",
                List.of(new ParameterCapture("password", "\"hunter2\"", true))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("login**(password: `[REDACTED]`)");
    assertThat(result).doesNotContain("hunter2");
  }

  @Test
  void repeatedLongValueDefinesReferenceOnceAndReusesIt() {
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("ExpenseValidator", "ensureValid", "expense", HOTEL),
                leaf("TripLedger", "recordExpense", "expense", HOTEL)));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("ensureValid**(expense: `‹v1›=" + HOTEL + "`)");
    assertThat(result).contains("recordExpense**(expense: `‹v1›`)");
  }
}
