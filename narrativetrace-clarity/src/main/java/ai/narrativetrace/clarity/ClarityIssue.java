/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

/**
 * One actionable naming issue discovered during clarity analysis.
 *
 * <p>INTENT: Keep issue reporting structured enough for Markdown, JSON, and quality gates to all
 * consume the same result.
 */
public record ClarityIssue(
    String category,
    String element,
    String suggestion,
    Severity severity,
    int occurrences,
    double impactScore) {
  public enum Severity {
    HIGH(3),
    MEDIUM(2),
    LOW(1);

    private final int weight;

    Severity(int weight) {
      this.weight = weight;
    }

    public int weight() {
      return weight;
    }
  }

  public ClarityIssue(String category, String element, String suggestion) {
    this(category, element, suggestion, Severity.MEDIUM, 1, Severity.MEDIUM.weight());
  }

  public ClarityIssue withOccurrences(int occurrences) {
    return new ClarityIssue(
        category, element, suggestion, severity, occurrences, severity.weight() * occurrences);
  }
}
