/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.clarity.ClarityIssue;
import java.util.List;

/**
 * Maps vocabulary violations into {@code non-canonical-term} clarity issues.
 *
 * <p>INTENT: Violations ride the existing clarity reporting surface unchanged — {@code
 * clarity-report.md}, {@code clarity-results.json}, and the {@code clarityCheck} gate all consume
 * {@link ClarityIssue}s, so vocabulary governance costs no new report format. Severity is {@code
 * MEDIUM} per the plan; occurrences aggregate into the impact score.
 */
public final class NonCanonicalTermIssues {

  /** Issue category consumed by clarity reports and gates. */
  public static final String CATEGORY = "non-canonical-term";

  private NonCanonicalTermIssues() {}

  /**
   * Converts violations to clarity issues, preserving order.
   *
   * @param violations aggregated violations of one run; must not be {@code null}
   * @return one issue per violation, element {@code context.site}, severity {@code MEDIUM}
   */
  public static List<ClarityIssue> from(List<VocabularyViolation> violations) {
    if (violations == null) {
      throw new IllegalArgumentException("violations must not be null");
    }
    return violations.stream().map(NonCanonicalTermIssues::toIssue).toList();
  }

  private static ClarityIssue toIssue(VocabularyViolation violation) {
    return new ClarityIssue(
            CATEGORY, violation.context() + "." + violation.site(), suggestion(violation))
        .withOccurrences(violation.occurrences());
  }

  private static String suggestion(VocabularyViolation violation) {
    var base = "use canonical term '" + violation.canonicalTerm() + "'";
    if (violation.suggestedIdentifier() == null) {
      return base;
    }
    return base + " → rename to " + violation.suggestedIdentifier();
  }
}
