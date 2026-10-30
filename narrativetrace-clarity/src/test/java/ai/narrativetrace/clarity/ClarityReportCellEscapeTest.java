/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-28-6 — the clarity report's tables interpolate the names they are reporting on
 * without escaping them, and a name is the one input this renderer is guaranteed to receive
 * something unusual in: it exists to diagnose naming. A pipe in an element name adds a column, a
 * newline adds a row.
 *
 * <p>INTENT: the product already knows the rule and spells it out twice. {@code
 * GlossaryMarkdownRenderer.cell} escapes pipes and collapses line breaks — "escapes table-breaking
 * characters" — and {@code RunIndex.cell} escapes pipes. {@code ClarityReportRenderer} escapes
 * nothing: the element goes into a code span, which does <em>not</em> protect a pipe in a
 * GitHub-flavoured table (the cell split happens before the span is parsed), and the scenario name
 * goes into a heading and a table cell raw.
 *
 * <p><b>@llmNote</b> Reachable from ordinary code, not just a hostile one: a Kotlin function name
 * is written in backticks and routinely carries spaces and punctuation ({@code `order fails |
 * refund issued`}), a JUnit 5 display name interpolates arguments, and a JVM method name is a
 * constant-pool string that no compiler but javac restricts to identifiers. The JSON twin of this
 * report ({@code clarity-results.json}) escapes the same strings properly, so the two artifacts of
 * one scan disagree — the asymmetry this repository treats as a defect class of its own.
 */
class ClarityReportCellEscapeTest {

  private final ClarityReportRenderer renderer = new ClarityReportRenderer();

  @Test
  @DisplayName("ADV-2026-10-28-6: a pipe in an element name may not add a column")
  void aPipeInAnElementNameKeepsTheIssuesRowsColumnCount() {
    var markdown = renderer.render("Service.process", resultWith("validate|refund"));

    var rows = markdown.lines().filter(line -> line.startsWith("| HIGH")).toList();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).chars().filter(c -> c == '|').count())
        .as("four cells, so five pipes — the header's own count")
        .isEqualTo(5);
  }

  @Test
  @DisplayName("ADV-2026-10-28-6: a newline in an element name may not add a row")
  void aNewlineInAnElementNameDoesNotForgeASecondIssueRow() {
    var markdown =
        renderer.render("Service.process", resultWith("validate\n| LOW | forged | `x` | none |"));

    assertThat(markdown).doesNotContain("\n| LOW | forged");
  }

  private static ClarityResult resultWith(String element) {
    var issue =
        new ClarityIssue(
            "method-name",
            element,
            "Use a domain-specific verb+noun",
            ClarityIssue.Severity.HIGH,
            1,
            3.0);
    return new ClarityResult(0.4, 0.0, 0.0, 0.0, 1.0, 0.7, List.of(issue));
  }
}
