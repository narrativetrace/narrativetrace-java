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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-28-5 — the folded half of the Markdown renderer writes method names into the document
 * without the escape the unfolded half applies. {@code MarkdownRenderer.signatureText} exists for
 * exactly this and says why in its own javadoc — "a raw newline in a name is document structure,
 * and the rendered Markdown is commonly viewed in a browser, where a raw {@code <img onerror=...>}
 * in a name is active HTML" — but {@code LoopFold}'s {@code — same flow (…)} tail appends {@code
 * child.signature().methodName()} raw.
 *
 * <p>INTENT: a parallel code path that escapes less than its sibling is the defect class this
 * repository names out loud: every behaviour of one path must be tested on the other, because
 * divergence between parallel paths is a top source of bugs. The two paths meet in one document
 * here — the first iteration of a folded run renders through {@code signatureText} and the
 * remaining iterations through {@code LoopFold} — so the same name is escaped on one line of the
 * trace and raw two lines below it.
 *
 * <p><b>@llmNote</b> A method name is not always a Java identifier: a JVM constructor is {@code
 * <init>}, a Kotlin test method is spelled in backticks and routinely carries spaces and
 * punctuation, and {@code MethodSignature} is a public record with no validation, so a replayed or
 * hand-built tree carries whatever it was given. The folding path is the default — {@code
 * narrativetrace.unfolded=false} — so this is the shape most runs produce.
 */
class LoopFoldFlowEscapeTest {

  private static final String ACTIVE_HTML = "<img src=x onerror=alert(1)>";

  @Test
  @DisplayName("ADV-2026-10-28-5: the fold summary escapes a name exactly as the call line does")
  void aNameInTheFoldedFlowTailIsHtmlEscapedLikeTheRenderedIterationsOwn() {
    var document = renderFoldedRunOf("validate" + ACTIVE_HTML);

    assertThat(document)
        .as("the unfolded iteration renders the same name escaped, two lines above")
        .contains("&lt;img src=x onerror=alert(1)&gt;")
        .doesNotContain(ACTIVE_HTML);
  }

  @Test
  @DisplayName("ADV-2026-10-28-5: the fold summary is one line whatever a name carries")
  void aNewlineInTheFoldedFlowTailDoesNotSplitTheSummaryLine() {
    var document = renderFoldedRunOf("validate\n- Ledger.refund()");

    var summary =
        document.lines().filter(line -> line.contains("same flow")).findFirst().orElseThrow();
    assertThat(summary)
        .as("the flow tail and the durations that follow it stay on one line")
        .contains("total");
  }

  @Test
  @DisplayName("ADV-2026-10-28-5: a value-derived fold label is escaped like the value it names")
  void anIdentityLabelMintedFromACapturedValueIsEscapedOnTheSummaryLine() {
    var document = renderFoldedRunLabelledBy("Dinner", "<img src=x onerror=a>");

    assertThat(document)
        .as("the label is minted from a captured value, which is why MarkdownEscape exists")
        .doesNotContain("<img src=x onerror=a>");
  }

  /** A run whose folded iterations are distinguished — and therefore named — by {@code label}. */
  private static String renderFoldedRunLabelledBy(String first, String label) {
    var children =
        List.of(
            labelledIteration(first), labelledIteration(label), labelledIteration(label + " 2"));
    return render(children);
  }

  private static TraceNode labelledIteration(String description) {
    var structured =
        new RenderedValue.ObjectVal(
            "Expense", java.util.Map.of("description", new RenderedValue.StringVal(description)));
    var child =
        new TraceNode(
            new MethodSignature("Validator", "validate", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            2_000_000L);
    return new TraceNode(
        new MethodSignature(
            "Service",
            "recordExpense",
            List.of(
                new ParameterCapture(
                    "expense",
                    "Expense(description: \"" + description + "\")",
                    false,
                    structured))),
        List.of(child),
        new TraceOutcome.Returned(null),
        5_000_000L);
  }

  /** Three same-shape iterations: one renders in full, the other two fold into the summary line. */
  private static String renderFoldedRunOf(String childMethodName) {
    var children =
        List.of(
            iteration(childMethodName, "Dinner"),
            iteration(childMethodName, "Taxi"),
            iteration(childMethodName, "Groceries"));
    return render(children);
  }

  private static String render(List<TraceNode> children) {
    var root =
        new TraceNode(
            new MethodSignature("Trip", "settle", List.of()),
            children,
            new TraceOutcome.Returned(null));
    return new MarkdownRenderer().render(new DefaultTraceTree(List.of(root)));
  }

  private static TraceNode iteration(String childMethodName, String description) {
    var child =
        new TraceNode(
            new MethodSignature("Validator", childMethodName, List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            2_000_000L);
    return new TraceNode(
        new MethodSignature(
            "Service",
            "recordExpense",
            List.of(new ParameterCapture("expense", "Expense(" + description + ")", false))),
        List.of(child),
        new TraceOutcome.Returned(null),
        5_000_000L);
  }
}
