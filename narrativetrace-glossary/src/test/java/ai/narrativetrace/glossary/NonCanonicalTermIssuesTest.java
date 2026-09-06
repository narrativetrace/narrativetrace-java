/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.clarity.ClarityIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class NonCanonicalTermIssuesTest {

  @Test
  void mapsViolationToMediumSeverityClarityIssueWithAggregatedOccurrences() {
    var violation =
        new VocabularyViolation(
            "billing",
            "account with overdraft",
            "overdraft account",
            "OverdraftService.openAccountWithOverdraft",
            "openAccountWithOverdraft",
            "openOverdraftAccount",
            3);

    var issues = NonCanonicalTermIssues.from(List.of(violation));

    assertThat(issues)
        .containsExactly(
            new ClarityIssue(
                    "non-canonical-term",
                    "billing.OverdraftService.openAccountWithOverdraft",
                    "use canonical term 'overdraft account' → rename to openOverdraftAccount")
                .withOccurrences(3));
    assertThat(issues.get(0).severity()).isEqualTo(ClarityIssue.Severity.MEDIUM);
    assertThat(issues.get(0).impactScore()).isEqualTo(6.0);
  }

  @Test
  void omitsRenameClauseWhenNoSuggestionExists() {
    var violation =
        new VocabularyViolation(
            "billing",
            "account with overdraft",
            "overdraft account",
            "A.legacy",
            "legacy",
            null,
            1);

    var issues = NonCanonicalTermIssues.from(List.of(violation));

    assertThat(issues.get(0).suggestion()).isEqualTo("use canonical term 'overdraft account'");
  }

  @Test
  void rejectsNullViolations() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> NonCanonicalTermIssues.from(null))
        .withMessageContaining("violations");
  }
}
