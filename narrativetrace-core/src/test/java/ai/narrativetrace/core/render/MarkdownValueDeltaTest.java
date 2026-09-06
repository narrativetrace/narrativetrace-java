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
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Intra-trace value deltas: when the same entity reappears in one trace slightly changed, the later
 * emission renders as a field-level diff against the in-document reference instead of a second full
 * blob, so the one changed field is the thing the reader sees.
 */
class MarkdownValueDeltaTest {

  private static final String DINNER_USD =
      "Expense(description: \"Dinner\", amount: 100.00, currency: \"USD\")";
  private static final String DINNER_EUR =
      "Expense(description: \"Dinner\", amount: 92.0000, currency: \"EUR\")";
  private static final String DINNER_GBP =
      "Expense(description: \"Dinner\", amount: 79.0000, currency: \"GBP\")";
  private static final String DELTA_TO_EUR = "{amount: 100.0→92.0, currency: \"USD\"→\"EUR\"}";

  private static RenderedValue object(String typeName, Object... nameThenValue) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    for (var i = 0; i < nameThenValue.length; i += 2) {
      fields.put((String) nameThenValue[i], (RenderedValue) nameThenValue[i + 1]);
    }
    return new RenderedValue.ObjectVal(typeName, fields);
  }

  private static RenderedValue text(String value) {
    return new RenderedValue.StringVal(value);
  }

  private static RenderedValue number(double value) {
    return new RenderedValue.DoubleVal(value);
  }

  private static RenderedValue expense(String description, double amount, String currency) {
    return object(
        "Expense",
        "description",
        text(description),
        "amount",
        number(amount),
        "currency",
        text(currency));
  }

  private static TraceNode leaf(String className, String method, String value, RenderedValue held) {
    return new TraceNode(
        new MethodSignature(
            className, method, List.of(new ParameterCapture("expense", value, false, held))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  private static TraceNode returning(
      String className, String method, String value, RenderedValue held) {
    return new TraceNode(
        new MethodSignature(className, method, List.of()),
        List.of(),
        new TraceOutcome.Returned(value, held),
        1_000_000L);
  }

  private static String render(TraceNode... nodes) {
    return new MarkdownRenderer().render(new DefaultTraceTree(List.of(nodes)));
  }

  @Test
  void changedScalarFieldsRenderAsDeltaAgainstTheReference() {
    var result =
        render(
            leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            leaf("ShareCalculator", "split", DINNER_EUR, expense("Dinner", 92.0, "EUR")));

    assertThat(result).contains("recordExpense**(expense: `‹Dinner›=" + DINNER_USD + "`)");
    assertThat(result).contains("split**(expense: `‹Dinner›′" + DELTA_TO_EUR + "`)");
  }

  @Test
  void repeatedChangedVariantIsDefinedAsADeltaOfTheReference() {
    var result =
        render(
            leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            leaf("ShareCalculator", "split", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
            leaf("AuditLog", "append", DINNER_EUR, expense("Dinner", 92.0, "EUR")));

    assertThat(result).contains("recordExpense**(expense: `‹Dinner›=" + DINNER_USD + "`)");
    assertThat(result).contains("split**(expense: `‹Dinner·2›=‹Dinner›′" + DELTA_TO_EUR + "`)");
    assertThat(result).contains("append**(expense: `‹Dinner·2›`)");
  }

  @Test
  void everyLaterVariantDiffsAgainstTheSameReference() {
    var result =
        render(
            leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            leaf("ShareCalculator", "split", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
            leaf("Reporter", "report", DINNER_GBP, expense("Dinner", 79.0, "GBP")));

    assertThat(result).contains("split**(expense: `‹Dinner›′" + DELTA_TO_EUR + "`)");
    assertThat(result)
        .contains("report**(expense: `‹Dinner›′{amount: 100.0→79.0, currency: \"USD\"→\"GBP\"}`)");
  }

  @Test
  void deltaRendersOnAReturnValueToo() {
    var result =
        render(
            returning("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            returning("ShareCalculator", "normalize", DINNER_EUR, expense("Dinner", 92.0, "EUR")));

    assertThat(result).contains("recordExpense**() → `‹Dinner›=" + DINNER_USD + "`");
    assertThat(result).contains("normalize**() → `‹Dinner›′" + DELTA_TO_EUR + "`");
  }

  private static RenderedValue trip(String name, double amount) {
    return object(
        "Trip",
        "name",
        text(name),
        "expenses",
        new RenderedValue.ListVal(List.of(object("Expense", "amount", number(amount)))));
  }

  @Test
  void changedNestedFieldFallsBackToTheFullRender() {
    var before = "Trip(name: \"Rome week\", expenses: [Expense(amount: 10.0)])";
    var after = "Trip(name: \"Rome week\", expenses: [Expense(amount: 20.0)])";

    var result =
        render(
            leaf("Planner", "plan", before, trip("Rome week", 10.0)),
            leaf("Planner", "replan", after, trip("Rome week", 20.0)));

    assertThat(result).contains("plan**(expense: `" + before + "`)");
    assertThat(result).contains("replan**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  @Test
  void differentFieldSetFallsBackToTheFullRender() {
    var before = "Order(id: \"order-77\", total: 10.0, currency: \"EUR\")";
    var after = "Order(id: \"order-77\", total: 10.0, coupon: \"SUMMER\")";
    var priced =
        object("Order", "id", text("order-77"), "total", number(10.0), "currency", text("EUR"));
    var couponed =
        object("Order", "id", text("order-77"), "total", number(10.0), "coupon", text("SUMMER"));

    var result =
        render(
            leaf("Checkout", "price", before, priced), leaf("Checkout", "apply", after, couponed));

    assertThat(result).contains("price**(expense: `" + before + "`)");
    assertThat(result).contains("apply**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  @Test
  void sameIdentityValueOnADifferentTypeIsNotTheSameEntity() {
    var refund = "Refund(description: \"Dinner\", amount: 100.00, currency: \"USD\")";
    var refundValue =
        object(
            "Refund",
            "description",
            text("Dinner"),
            "amount",
            number(100.0),
            "currency",
            text("USD"));

    var result =
        render(
            leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            leaf("TripLedger", "recordRefund", refund, refundValue));

    assertThat(result).contains("recordExpense**(expense: `" + DINNER_USD + "`)");
    assertThat(result).contains("recordRefund**(expense: `" + refund + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  private static RenderedValue anonymousExpense(double amount, String currency) {
    return object(
        "Expense", "amount", number(amount), "currency", text(currency), "category", text("FOOD"));
  }

  @Test
  void valueWithoutAnIdentityFieldIsNeverADelta() {
    var before = "Expense(amount: 100.00, currency: \"USD\", category: \"FOOD\")";
    var after = "Expense(amount: 92.0000, currency: \"EUR\", category: \"FOOD\")";

    var result =
        render(
            leaf("TripLedger", "recordExpense", before, anonymousExpense(100.0, "USD")),
            leaf("ShareCalculator", "split", after, anonymousExpense(92.0, "EUR")));

    assertThat(result).contains("recordExpense**(expense: `" + before + "`)");
    assertThat(result).contains("split**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  @Test
  void redactedIdentityFieldNeverAnchorsADelta() {
    var before = "Expense(description: [REDACTED], amount: 100.00, currency: \"USD\")";
    var after = "Expense(description: [REDACTED], amount: 92.0000, currency: \"EUR\")";

    var result =
        render(
            leaf(
                "TripLedger",
                "recordExpense",
                before,
                expense(RedactionPolicy.MARKER, 100.0, "USD")),
            leaf("ShareCalculator", "split", after, expense(RedactionPolicy.MARKER, 92.0, "EUR")));

    assertThat(result).contains("recordExpense**(expense: `" + before + "`)");
    assertThat(result).contains("split**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  private static RenderedValue order(long count, boolean active, RenderedValue note, long millis) {
    return object(
        "Order",
        "id",
        text("order-77"),
        "count",
        new RenderedValue.LongVal(count),
        "active",
        new RenderedValue.BooleanVal(active),
        "note",
        note,
        "at",
        new RenderedValue.InstantVal(millis));
  }

  @Test
  void everyScalarKindFormatsTheWayTheFlatRendererPrintsIt() {
    var before =
        "Order(id: \"order-77\", count: 1, active: true, note: null, at: 2020-01-01T00:00:00Z)";
    var after =
        "Order(id: \"order-77\", count: 2, active: false, note: \"rush\", at: 2020-01-02T00:00:00Z)";

    var result =
        render(
            leaf(
                "Warehouse",
                "receive",
                before,
                order(1, true, new RenderedValue.NullVal(), 1_577_836_800_000L)),
            leaf("Warehouse", "ship", after, order(2, false, text("rush"), 1_577_923_200_000L)));

    assertThat(result)
        .contains(
            "ship**(expense: `‹order-77›′{count: 1→2, active: true→false, note: null→\"rush\","
                + " at: 2020-01-01T00:00:00Z→2020-01-02T00:00:00Z}`)");
  }

  private static RenderedValue note(RenderedValue body) {
    return object("Note", "title", text("Standup"), "body", body);
  }

  @Test
  void longStringValuesAreElidedInsideTheDelta() {
    var sixtyOne = "x".repeat(61);
    var before = "Note(title: \"Standup\", body: \"short body of the daily note\")";
    var after = "Note(title: \"Standup\", body: \"" + sixtyOne + "\")";

    var result =
        render(
            leaf("Journal", "write", before, note(text("short body of the daily note"))),
            leaf("Journal", "amend", after, note(text(sixtyOne))));

    assertThat(result)
        .contains(
            "amend**(expense: `‹Standup›′{body: \"short body of the daily note\"→\""
                + "x".repeat(60)
                + "…\"}`)");
  }

  @Test
  void controlCharactersInsideADeltaValueAreEscaped() {
    var before = "Note(title: \"Standup\", body: \"first line of the standup note\")";
    var after = "Note(title: \"Standup\", body: \"second\\nline of the standup note\")";

    var result =
        render(
            leaf("Journal", "write", before, note(text("first line of the standup note"))),
            leaf("Journal", "amend", after, note(text("second\nline of the standup note"))));

    assertThat(result).contains("→\"second\\nline of the standup note\"}`)");
    assertThat(result).doesNotContain("second\nline");
  }

  @Test
  void identicalStructuredFormsWithDifferentBytesFallBackToTheFullRender() {
    var before = "Expense(description: \"Dinner\", amount: 100.00, currency: \"USD\")";
    var after = "Expense(description: \"Dinner\", amount: 100.0, currency: \"USD\")";
    var same = expense("Dinner", 100.0, "USD");

    var result =
        render(
            leaf("TripLedger", "recordExpense", before, same),
            leaf("ShareCalculator", "split", after, same));

    assertThat(result).contains("recordExpense**(expense: `" + before + "`)");
    assertThat(result).contains("split**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  private static RenderedValue fee(double amount) {
    return object("Fee", "name", text("Bank"), "amount", number(amount));
  }

  @Test
  void valuesBelowTheReferenceLengthNeverBecomeDeltas() {
    var before = "Fee(name: \"Bank\", amount: 1.0)";
    var after = "Fee(name: \"Bank\", amount: 2.0)";

    var result =
        render(leaf("Bank", "charge", before, fee(1.0)), leaf("Bank", "refund", after, fee(2.0)));

    assertThat(result).contains("charge**(expense: `" + before + "`)");
    assertThat(result).contains("refund**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  @Test
  void deltaSurvivesTheDocumentRenderingPath() {
    var tree =
        new DefaultTraceTree(
            List.of(
                leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
                leaf("ShareCalculator", "split", DINNER_EUR, expense("Dinner", 92.0, "EUR"))));

    var result =
        new MarkdownRenderer()
            .renderDocument(tree, new TraceMetadata("trip split", ScenarioResult.SUCCESS));

    assertThat(result).contains("split**(expense: `‹Dinner›′" + DELTA_TO_EUR + "`)");
  }

  @Test
  void aContainedReferenceAndItsDeltaShareOneLabel() {
    var listOfOne = "[" + DINNER_USD + "]";

    var result =
        render(
            leaf("TripLedger", "recordExpense", DINNER_USD, expense("Dinner", 100.00, "USD")),
            returning("TripLedger", "expensesOf", listOfOne, null),
            leaf("ShareCalculator", "split", DINNER_EUR, expense("Dinner", 92.0, "EUR")));

    assertThat(result).contains("recordExpense**(expense: `‹Dinner›=" + DINNER_USD + "`)");
    assertThat(result).contains("expensesOf**() → `[‹Dinner›]`");
    assertThat(result).contains("split**(expense: `‹Dinner›′" + DELTA_TO_EUR + "`)");
  }

  private static TraceNode forkMember(
      String className, String method, String value, RenderedValue held) {
    return new TraceNode(
        new MethodSignature(
            className, method, List.of(new ParameterCapture("expense", value, false, held))),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L,
        0L,
        new ConcurrencyInfo("fork-1", "pool-1", 7, false, ConcurrencyKind.FORK_JOIN));
  }

  @Test
  void deltaFollowsTheRenderOrderOfForkedMembers() {
    var root =
        new TraceNode(
            new MethodSignature("Trip", "settle", List.of()),
            List.of(
                forkMember("Alpha", "record", DINNER_USD, expense("Dinner", 100.00, "USD")),
                forkMember("Beta", "convert", DINNER_EUR, expense("Dinner", 92.0, "EUR"))),
            new TraceOutcome.Returned(null),
            2_000_000L);

    var result = new MarkdownRenderer().render(new DefaultTraceTree(List.of(root)));

    assertThat(result).contains("Alpha.record**(expense: `‹Dinner›=" + DINNER_USD + "`)");
    assertThat(result).contains("Beta.convert**(expense: `‹Dinner›′" + DELTA_TO_EUR + "`)");
  }

  @Test
  void aLongScalarStructuredValueIsNeverAnEntityToDiffAgainst() {
    var before = "\"a narrative summary line that is well past the reference length\"";
    var after = "\"a narrative summary line that is well past the reference limit\"";

    var result =
        render(
            leaf("Journal", "write", before, text(before)),
            leaf("Journal", "amend", after, text(after)));

    assertThat(result).contains("write**(expense: `" + before + "`)");
    assertThat(result).contains("amend**(expense: `" + after + "`)");
    assertThat(result).doesNotContain("′").doesNotContain("‹");
  }

  private static TraceNode parent(String className, String method, TraceNode... children) {
    return new TraceNode(
        new MethodSignature(className, method, List.of()),
        List.of(children),
        new TraceOutcome.Returned(null),
        3_000_000L);
  }

  @Test
  void foldedIterationsAreNamedByTheirDeltaNotByABareLabel() {
    var result =
        new MarkdownRenderer()
            .render(
                new DefaultTraceTree(
                    List.of(
                        parent(
                            "Trip",
                            "settle",
                            leaf("Ledger", "record", DINNER_USD, expense("Dinner", 100.00, "USD")),
                            leaf("Ledger", "record", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
                            leaf(
                                "Ledger", "record", DINNER_GBP, expense("Dinner", 79.0, "GBP"))))));

    assertThat(result).contains("record**(expense: `‹Dinner›=" + DINNER_USD + "`)");
    assertThat(result)
        .contains(
            "×2 more: ‹Dinner›′"
                + DELTA_TO_EUR
                + ", ‹Dinner›′{amount: 100.0→79.0, currency: \"USD\"→\"GBP\"}");
  }

  @Test
  void aFoldedIterationAlreadyCarryingALabelKeepsIt() {
    var result =
        new MarkdownRenderer()
            .render(
                new DefaultTraceTree(
                    List.of(
                        leaf("Inbox", "receive", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
                        leaf("Inbox", "queue", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
                        parent(
                            "Trip",
                            "settle",
                            leaf("Ledger", "record", DINNER_USD, expense("Dinner", 100.00, "USD")),
                            leaf(
                                "Ledger", "record", DINNER_EUR, expense("Dinner", 92.0, "EUR"))))));

    assertThat(result).contains("receive**(expense: `‹Dinner›=" + DINNER_EUR + "`)");
    assertThat(result)
        .contains("record**(expense: `‹Dinner›′{amount: 92.0→100.0, currency: \"EUR\"→\"USD\"}`)");
    assertThat(result).contains("×1 more: ‹Dinner›");
  }

  @Test
  void aFoldedIterationPrefersItsOwnLabelOverADiffAgainstAnotherBaseline() {
    var result =
        new MarkdownRenderer()
            .render(
                new DefaultTraceTree(
                    List.of(
                        leaf("Ledger", "record", DINNER_USD, expense("Dinner", 100.00, "USD")),
                        leaf("Inbox", "queue", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
                        leaf("Inbox", "file", DINNER_EUR, expense("Dinner", 92.0, "EUR")),
                        parent(
                            "Trip",
                            "settle",
                            leaf("Ledger", "record", DINNER_USD, expense("Dinner", 100.00, "USD")),
                            leaf(
                                "Ledger", "record", DINNER_EUR, expense("Dinner", 92.0, "EUR"))))));

    assertThat(result).contains("queue**(expense: `‹Dinner·2›=‹Dinner›′" + DELTA_TO_EUR + "`)");
    assertThat(result).contains("×1 more: ‹Dinner·2›");
  }
}
