/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Markdown renderer for clarity results.
 *
 * <p>INTENT: Use this for reports meant to be read in CI artifacts or checked into narrative output
 * directories alongside trace files.
 */
public final class ClarityReportRenderer {

  private static final double LOW_SCORE_THRESHOLD = 0.7;

  public String render(String scenarioName, ClarityResult result) {
    var sb = new StringBuilder();

    sb.append("# Clarity Report: ").append(scenarioName).append("\n\n");

    appendScoresTable(sb, result);
    appendElementsSection(sb, result.elementNotes());

    if (!result.issues().isEmpty()) {
      sb.append("\n## Issues\n\n");
      appendIssuesTable(sb, result.issues());
    }

    return sb.toString().stripTrailing();
  }

  /**
   * Renders a suite report from a map of unique scenario names to results.
   *
   * <p>When the same scenario name appears in multiple test classes use {@link
   * #renderSuiteReport(List)} to preserve all entries.
   */
  public String renderSuiteReport(Map<String, ClarityResult> results) {
    return renderSuiteReport(List.copyOf(results.entrySet()));
  }

  /**
   * Renders a suite report from an ordered list of (scenarioName, result) pairs.
   *
   * <p>Duplicate scenario names are preserved as separate rows, which is required for correct
   * suite-level reporting across test classes that share display names.
   */
  public String renderSuiteReport(List<Map.Entry<String, ClarityResult>> results) {
    return renderSuiteReport(results, List.of());
  }

  /**
   * Renders a suite report with an additional "Suite Issues" section for issues that belong to the
   * whole run rather than to any single scenario (e.g. {@code non-canonical-term} vocabulary
   * violations from the glossary harvest).
   *
   * <p>Suite issues are rendered as their own table after the per-scenario sections, so they never
   * skew the scenario score table. An empty {@code suiteIssues} list omits the section entirely.
   */
  public String renderSuiteReport(
      List<Map.Entry<String, ClarityResult>> results, List<ClarityIssue> suiteIssues) {
    var sb = new StringBuilder();

    sb.append("# Clarity Suite Report\n\n");

    var sorted =
        results.stream()
            .sorted(Comparator.comparingDouble(e -> e.getValue().overallScore()))
            .toList();

    appendScenarioScoreTable(sb, sorted);
    appendScenarioDetailSections(sb, sorted);
    appendSuiteIssuesSection(sb, suiteIssues);

    return sb.toString().stripTrailing();
  }

  private void appendScenarioScoreTable(
      StringBuilder sb, List<Map.Entry<String, ClarityResult>> sorted) {
    sb.append("## Scenarios\n\n");
    sb.append("| Scenario | Score |\n");
    sb.append("|----------|-------|\n");
    for (var entry : sorted) {
      sb.append(String.format("| %s | %.2f |%n", entry.getKey(), entry.getValue().overallScore()));
    }
  }

  /**
   * Per-scenario detail. The Issues table stays gated to low-score scenarios (an actionable backlog
   * for the names that need work), but the Elements table renders for EVERY scenario that has
   * notes, including high scorers — a bare 0.86 with no explanation is the bug this fixes.
   */
  private void appendScenarioDetailSections(
      StringBuilder sb, List<Map.Entry<String, ClarityResult>> sorted) {
    for (var entry : sorted) {
      appendScenarioSection(sb, entry.getKey(), entry.getValue());
    }
  }

  private void appendScenarioSection(StringBuilder sb, String name, ClarityResult result) {
    boolean lowWithIssues =
        result.overallScore() < LOW_SCORE_THRESHOLD && !result.issues().isEmpty();
    boolean hasElements = !result.elementNotes().isEmpty();
    if (!lowWithIssues && !hasElements) {
      return;
    }
    sb.append(String.format("%n### %s%n", name));
    if (lowWithIssues) {
      sb.append("\n");
      appendScoresTable(sb, result);
      sb.append("\n");
      appendIssuesTable(sb, result.issues());
    }
    appendElementsSection(sb, result.elementNotes());
  }

  private void appendElementsSection(StringBuilder sb, List<ElementNote> notes) {
    if (notes.isEmpty()) {
      return;
    }
    sb.append("\n## Elements\n\n");
    sb.append("| Element | Score | Note |\n");
    sb.append("|---------|-------|------|\n");
    for (var note : notes) {
      sb.append(String.format("| `%s` | %.2f | %s |%n", note.element(), note.score(), note.note()));
    }
  }

  private void appendSuiteIssuesSection(StringBuilder sb, List<ClarityIssue> suiteIssues) {
    if (suiteIssues.isEmpty()) {
      return;
    }
    sb.append("\n## Suite Issues\n\n");
    appendIssuesTable(sb, suiteIssues);
  }

  private void appendScoresTable(StringBuilder sb, ClarityResult result) {
    sb.append("## Scores\n\n");
    sb.append("| Category | Score | Weight | Weighted |\n");
    sb.append("|----------|-------|--------|----------|\n");
    sb.append(
        String.format(
            "| Method Names | %.2f | 0.30 | %.2f |%n",
            result.methodNameScore(), result.methodNameScore() * 0.30));
    sb.append(
        String.format(
            "| Class Names | %.2f | 0.20 | %.2f |%n",
            result.classNameScore(), result.classNameScore() * 0.20));
    sb.append(
        String.format(
            "| Parameter Names | %.2f | 0.25 | %.2f |%n",
            result.parameterNameScore(), result.parameterNameScore() * 0.25));
    sb.append(
        String.format(
            "| Structural | %.2f | 0.15 | %.2f |%n",
            result.structuralScore(), result.structuralScore() * 0.15));
    sb.append(
        String.format(
            "| Cohesion | %.2f | 0.10 | %.2f |%n",
            result.cohesionScore(), result.cohesionScore() * 0.10));
    sb.append(String.format("| **Overall** | **%.2f** | | |%n", result.overallScore()));
  }

  private void appendIssuesTable(StringBuilder sb, List<ClarityIssue> issues) {
    var sorted =
        issues.stream().sorted((a, b) -> Double.compare(b.impactScore(), a.impactScore())).toList();

    sb.append("| Severity | Category | Element | Suggestion |\n");
    sb.append("|----------|----------|---------|------------|\n");
    for (var issue : sorted) {
      String elementDisplay =
          issue.occurrences() > 1
              ? String.format("`%s` (x%d)", issue.element(), issue.occurrences())
              : String.format("`%s`", issue.element());
      sb.append(
          String.format(
              "| %s | %s | %s | %s |%n",
              issue.severity(), issue.category(), elementDisplay, issue.suggestion()));
    }
  }
}
