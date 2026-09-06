/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.List;

/**
 * Formats the vocabulary line of the suite console summary.
 *
 * <p>INTENT: Same role as core's {@code ConsoleSummaryReporter} — keep JUnit extensions free of
 * formatting detail while producing a stable, human-readable footer line:
 *
 * <pre>
 * Vocabulary: 3 new terms harvested, 2 deprecated synonyms in use
 *   accountWithOverdraft → use overdraftAccount (billing: "overdraft account")
 * </pre>
 */
public final class VocabularySummaryFormatter {

  /**
   * Formats the vocabulary summary for one harvest run.
   *
   * @param newTermCount number of terms this run added to the glossary; must be non-negative
   * @param violations deprecated-synonym uses, one detail line each; must not be {@code null}
   * @return the summary block without a trailing newline
   */
  public String formatSummary(int newTermCount, List<VocabularyViolation> violations) {
    if (newTermCount < 0) {
      throw new IllegalArgumentException("newTermCount must be non-negative: " + newTermCount);
    }
    if (violations == null) {
      throw new IllegalArgumentException("violations must not be null");
    }
    var out = new StringBuilder();
    out.append("Vocabulary: ")
        .append(newTermCount)
        .append(newTermCount == 1 ? " new term harvested" : " new terms harvested");
    if (!violations.isEmpty()) {
      out.append(", ")
          .append(violations.size())
          .append(
              violations.size() == 1
                  ? " deprecated synonym in use"
                  : " deprecated synonyms in use");
      violations.forEach(violation -> out.append("\n  ").append(detailLine(violation)));
    }
    return out.toString();
  }

  private static String detailLine(VocabularyViolation violation) {
    if (violation.suggestedIdentifier() != null) {
      return violation.identifier()
          + " → use "
          + violation.suggestedIdentifier()
          + " ("
          + violation.context()
          + ": \""
          + violation.canonicalTerm()
          + "\")";
    }
    return violation.identifier()
        + " → use canonical term \""
        + violation.canonicalTerm()
        + "\" ("
        + violation.context()
        + ")";
  }
}
