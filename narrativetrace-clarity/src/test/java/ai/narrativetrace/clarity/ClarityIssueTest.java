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
import org.junit.jupiter.api.Test;

class ClarityIssueTest {

  @Test
  void assignsHighSeverityToMeaninglessName() {
    var issue =
        new ClarityIssue(
            "param-name", "data", "Use specific name", ClarityIssue.Severity.HIGH, 1, 3.0);
    assertThat(issue.severity()).isEqualTo(ClarityIssue.Severity.HIGH);
  }

  @Test
  void assignsMediumSeverityToAbbreviation() {
    var issue =
        new ClarityIssue(
            "param-name", "custId", "Expand abbreviation", ClarityIssue.Severity.MEDIUM, 1, 2.0);
    assertThat(issue.severity()).isEqualTo(ClarityIssue.Severity.MEDIUM);
  }

  @Test
  void assignsLowSeverityToTypedGeneric() {
    var issue =
        new ClarityIssue(
            "param-name", "count", "Add qualifying prefix", ClarityIssue.Severity.LOW, 1, 1.0);
    assertThat(issue.severity()).isEqualTo(ClarityIssue.Severity.LOW);
  }

  @Test
  void deduplicatesRepeatedParam() {
    var issue =
        new ClarityIssue(
            "param-name", "data", "Use specific name", ClarityIssue.Severity.HIGH, 1, 3.0);
    var merged = issue.withOccurrences(5);
    assertThat(merged.occurrences()).isEqualTo(5);
    assertThat(merged.impactScore()).isEqualTo(15.0);
  }

  @Test
  void ranksIssuesByImpactScore() {
    var highWith2 = new ClarityIssue("param-name", "data", "a", ClarityIssue.Severity.HIGH, 2, 6.0);
    var lowWith5 = new ClarityIssue("param-name", "count", "b", ClarityIssue.Severity.LOW, 5, 5.0);
    var sorted =
        List.of(highWith2, lowWith5).stream()
            .sorted((a, b) -> Double.compare(b.impactScore(), a.impactScore()))
            .toList();
    assertThat(sorted.get(0).element()).isEqualTo("data");
  }

  @Test
  void computesImpactScore() {
    var issue = new ClarityIssue("param-name", "data", "fix", ClarityIssue.Severity.HIGH, 2, 6.0);
    assertThat(issue.impactScore()).isEqualTo(6.0);
  }

  @Test
  void threeArgConstructorDefaultsToMediumSeverity() {
    var issue = new ClarityIssue("method-name", "process", "fix it");
    assertThat(issue.severity()).isEqualTo(ClarityIssue.Severity.MEDIUM);
    assertThat(issue.occurrences()).isEqualTo(1);
    assertThat(issue.impactScore()).isEqualTo(2.0);
  }
}
