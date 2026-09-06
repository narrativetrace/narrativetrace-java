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
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClarityReportRendererTest {

  private final ClarityReportRenderer renderer = new ClarityReportRenderer();

  @Test
  void rendersOverallScoreTable() {
    var result = new ClarityResult(0.85, 1.0, 0.9, 0.7, 0.8, 0.9, List.of());

    var markdown = renderer.render("OrderService.calculateTotal", result);

    assertThat(markdown).contains("# Clarity Report: OrderService.calculateTotal");
    assertThat(markdown).contains("| Category | Score | Weight | Weighted |");
    assertThat(markdown).contains("| Method Names | 1.00 | 0.30 | 0.30 |");
    assertThat(markdown).contains("| Class Names | 0.90 | 0.20 | 0.18 |");
    assertThat(markdown).contains("| Parameter Names | 0.70 | 0.25 | 0.18 |");
    assertThat(markdown).contains("| Structural | 0.80 | 0.15 | 0.12 |");
    assertThat(markdown).contains("| Cohesion | 0.90 | 0.10 | 0.09 |");
    assertThat(markdown).contains("| **Overall** |");
    assertThat(markdown).contains("| **0.85** |");
  }

  @Test
  void rendersIssuesTableWithSuggestions() {
    var issues =
        List.of(
            new ClarityIssue(
                "method-name",
                "Service.process",
                "Use a domain-specific verb+noun",
                ClarityIssue.Severity.HIGH,
                1,
                3.0),
            new ClarityIssue(
                "param-name",
                "data",
                "Use a domain-specific name",
                ClarityIssue.Severity.HIGH,
                1,
                3.0));
    var result = new ClarityResult(0.4, 0.0, 0.0, 0.0, 1.0, 0.7, issues);

    var markdown = renderer.render("Service.process", result);

    assertThat(markdown).contains("## Issues");
    assertThat(markdown).contains("| Severity | Category | Element | Suggestion |");
    assertThat(markdown).contains("| HIGH | method-name | `Service.process` |");
    assertThat(markdown).contains("| HIGH | param-name | `data` |");
  }

  @Test
  void rendersSuiteReportWithLowestClarityScenarios() {
    var highClarity = new ClarityResult(0.95, 1.0, 1.0, 0.9, 1.0, 1.0, List.of());
    var lowClarity =
        new ClarityResult(
            0.25,
            0.0,
            0.0,
            0.0,
            1.0,
            0.7,
            List.of(
                new ClarityIssue(
                    "method-name",
                    "Service.process",
                    "Use a domain-specific verb+noun",
                    ClarityIssue.Severity.HIGH,
                    1,
                    3.0)));
    var results =
        Map.of(
            "OrderService.calculateTotal", highClarity,
            "Service.process", lowClarity);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).contains("# Clarity Suite Report");
    assertThat(markdown).contains("| Scenario | Score |");
    assertThat(markdown).contains("| Service.process | 0.25 |");
    assertThat(markdown).contains("| OrderService.calculateTotal | 0.95 |");
    assertThat(markdown).contains("### Service.process");
    assertThat(markdown).contains("| HIGH | method-name | `Service.process` |");
  }

  @Test
  void rendersCohesionRow() {
    var result = new ClarityResult(0.85, 1.0, 0.9, 0.7, 0.8, 0.9, List.of());

    var markdown = renderer.render("TestScenario", result);

    assertThat(markdown).contains("| Cohesion | 0.90 | 0.10 |");
  }

  @Test
  void rendersSeverityColumn() {
    var issues =
        List.of(
            new ClarityIssue(
                "method-name", "Service.process", "Fix it", ClarityIssue.Severity.HIGH, 1, 3.0));
    var result = new ClarityResult(0.4, 0.0, 0.0, 0.0, 1.0, 0.7, issues);

    var markdown = renderer.render("Test", result);

    assertThat(markdown).contains("| Severity |");
    assertThat(markdown).contains("| HIGH |");
  }

  @Test
  void rendersIssuesSortedByImpact() {
    var issues =
        List.of(
            new ClarityIssue("param-name", "count", "low", ClarityIssue.Severity.LOW, 1, 1.0),
            new ClarityIssue("method-name", "process", "high", ClarityIssue.Severity.HIGH, 3, 9.0));
    var result = new ClarityResult(0.3, 0.0, 0.0, 0.0, 1.0, 0.7, issues);

    var markdown = renderer.render("Test", result);

    int processPos = markdown.indexOf("`process`");
    int countPos = markdown.indexOf("`count`");
    assertThat(processPos).isLessThan(countPos);
  }

  @Test
  void rendersOccurrencesCount() {
    var issues =
        List.of(new ClarityIssue("param-name", "data", "fix", ClarityIssue.Severity.HIGH, 3, 9.0));
    var result = new ClarityResult(0.3, 0.0, 0.0, 0.0, 1.0, 0.7, issues);

    var markdown = renderer.render("Test", result);

    assertThat(markdown).contains("(x3)");
  }

  @Test
  void suiteReportIncludesCohesion() {
    var lowClarity =
        new ClarityResult(
            0.25,
            0.0,
            0.0,
            0.0,
            1.0,
            0.3,
            List.of(
                new ClarityIssue(
                    "method-name", "Service.process", "fix", ClarityIssue.Severity.HIGH, 1, 3.0)));
    var results = Map.of("Service.process", lowClarity);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).contains("| Cohesion | 0.30 | 0.10 |");
  }

  @Test
  void suiteReportSkipsDetailSectionForLowScoreWithNoIssues() {
    var lowNoIssues = new ClarityResult(0.3, 0.3, 0.3, 0.3, 0.3, 0.3, List.of());
    var results = Map.of("Service.empty", lowNoIssues);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).contains("| Service.empty | 0.30 |");
    assertThat(markdown).doesNotContain("### Service.empty");
  }

  @Test
  void suiteReportSortsScenariosAscendingByScore() {
    var low = new ClarityResult(0.20, 0.0, 0.0, 0.0, 1.0, 0.7, List.of());
    var mid = new ClarityResult(0.50, 0.5, 0.5, 0.5, 1.0, 0.7, List.of());
    var high = new ClarityResult(0.90, 1.0, 1.0, 0.9, 1.0, 1.0, List.of());
    var results = Map.of("High", high, "Mid", mid, "Low", low);

    var markdown = renderer.renderSuiteReport(results);

    int lowPos = markdown.indexOf("| Low |");
    int midPos = markdown.indexOf("| Mid |");
    int highPos = markdown.indexOf("| High |");
    assertThat(lowPos).isLessThan(midPos);
    assertThat(midPos).isLessThan(highPos);
  }

  @Test
  void suiteReportRendersSuiteIssuesSection() {
    var results =
        List.of(
            Map.entry(
                "places order",
                (ClarityResult) new ClarityResult(0.9, 1.0, 0.9, 0.9, 1.0, 0.9, List.of())));
    var suiteIssues =
        List.of(
            new ClarityIssue(
                    "non-canonical-term",
                    "billing.OverdraftService.openAccountWithOverdraft",
                    "use canonical term 'overdraft account' → rename to openOverdraftAccount")
                .withOccurrences(3));

    var markdown = renderer.renderSuiteReport(results, suiteIssues);

    assertThat(markdown).contains("## Suite Issues");
    assertThat(markdown)
        .contains(
            "| MEDIUM | non-canonical-term | `billing.OverdraftService.openAccountWithOverdraft`"
                + " (x3) | use canonical term 'overdraft account' → rename to"
                + " openOverdraftAccount |");
  }

  @Test
  void suiteReportOmitsSuiteIssuesSectionWhenEmpty() {
    var results =
        List.of(
            Map.entry(
                "places order",
                (ClarityResult) new ClarityResult(0.9, 1.0, 0.9, 0.9, 1.0, 0.9, List.of())));

    var markdown = renderer.renderSuiteReport(results, List.of());

    assertThat(markdown).doesNotContain("## Suite Issues");
  }

  @Test
  void suiteReportWithoutSuiteIssuesOverloadNeverRendersTheSection() {
    var lowClarity =
        new ClarityResult(
            0.25,
            0.0,
            0.0,
            0.0,
            1.0,
            0.3,
            List.of(
                new ClarityIssue(
                    "method-name", "Service.process", "fix", ClarityIssue.Severity.HIGH, 1, 3.0)));

    var markdown = renderer.renderSuiteReport(Map.of("Service.process", lowClarity));

    assertThat(markdown).doesNotContain("## Suite Issues");
  }

  @Test
  void suiteIssuesRenderSortedByImpactDescending() {
    var suiteIssues =
        List.of(
            new ClarityIssue("non-canonical-term", "shop.Cart.addItm", "use 'item'")
                .withOccurrences(1),
            new ClarityIssue("non-canonical-term", "shop.Cart.chkOut", "use 'check out'")
                .withOccurrences(5));

    var markdown = renderer.renderSuiteReport(List.of(), suiteIssues);

    assertThat(markdown.indexOf("`shop.Cart.chkOut`"))
        .isLessThan(markdown.indexOf("`shop.Cart.addItm`"));
  }

  @Test
  void suiteIssuesSectionRendersAfterLowScoreDetailSections() {
    var lowClarity =
        new ClarityResult(
            0.25,
            0.0,
            0.0,
            0.0,
            1.0,
            0.3,
            List.of(
                new ClarityIssue(
                    "method-name", "Service.process", "fix", ClarityIssue.Severity.HIGH, 1, 3.0)));
    var results = List.of(Map.entry("Service.process", (ClarityResult) lowClarity));
    var suiteIssues =
        List.of(new ClarityIssue("non-canonical-term", "shop.Cart.addItm", "use 'item'"));

    var markdown = renderer.renderSuiteReport(results, suiteIssues);

    assertThat(markdown.indexOf("### Service.process"))
        .isLessThan(markdown.indexOf("## Suite Issues"));
  }

  @Test
  void renderIncludesElementsTableForEveryElement() {
    var notes =
        List.of(
            new ElementNote(
                "method",
                "OrderService.calculateTotal",
                0.86,
                "Domain verb 'calculate' + broad noun 'total'"));
    var result = new ClarityResult(0.86, 1.0, 0.9, 0.7, 0.8, 0.9, List.of(), notes);

    var markdown = renderer.render("OrderService", result);

    assertThat(markdown).contains("## Elements");
    assertThat(markdown).contains("| Element | Score | Note |");
    assertThat(markdown)
        .contains(
            "| `OrderService.calculateTotal` | 0.86 |"
                + " Domain verb 'calculate' + broad noun 'total' |");
  }

  @Test
  void renderOmitsElementsSectionWhenNoNotes() {
    var result = new ClarityResult(0.85, 1.0, 0.9, 0.7, 0.8, 0.9, List.of());

    var markdown = renderer.render("Scenario", result);

    assertThat(markdown).doesNotContain("## Elements");
  }

  @Test
  void suiteReportRendersElementsForEveryScenarioIncludingHighScores() {
    var notes = List.of(new ElementNote("class", "OrderService", 0.90, "Role suffix 'Service'"));
    var highWithNotes = new ClarityResult(0.90, 1.0, 0.9, 0.9, 1.0, 0.9, List.of(), notes);
    var results = Map.of("places order", highWithNotes);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).contains("### places order");
    assertThat(markdown).contains("| Element | Score | Note |");
    assertThat(markdown).contains("| `OrderService` | 0.90 | Role suffix 'Service' |");
  }

  @Test
  void suiteReportKeepsIssueTablesGatedToLowScoresWhileShowingElementsForAll() {
    var notes = List.of(new ElementNote("class", "OrderService", 0.90, "Role suffix 'Service'"));
    var highWithNotes = new ClarityResult(0.90, 1.0, 0.9, 0.9, 1.0, 0.9, List.of(), notes);
    var results = Map.of("places order", highWithNotes);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).doesNotContain("## Scores");
    assertThat(markdown).doesNotContain("| Severity | Category | Element | Suggestion |");
  }

  @Test
  void suiteReportExactlyAtThresholdHasNoDetailSection() {
    // Score exactly 0.70 → NOT < 0.70 → no detail section
    var atThreshold =
        new ClarityResult(
            0.70,
            0.7,
            0.7,
            0.7,
            1.0,
            0.7,
            List.of(
                new ClarityIssue(
                    "method-name", "Something", "fix", ClarityIssue.Severity.LOW, 1, 1.0)));
    var results = Map.of("AtThreshold", atThreshold);

    var markdown = renderer.renderSuiteReport(results);

    assertThat(markdown).contains("| AtThreshold | 0.70 |");
    assertThat(markdown).doesNotContain("### AtThreshold");
  }
}
