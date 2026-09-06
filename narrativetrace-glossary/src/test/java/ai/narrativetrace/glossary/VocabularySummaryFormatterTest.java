/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VocabularySummaryFormatterTest {

  private final VocabularySummaryFormatter formatter = new VocabularySummaryFormatter();

  private static VocabularyViolation violation(String identifier, String suggested) {
    return new VocabularyViolation(
        "billing",
        "account with overdraft",
        "overdraft account",
        "AccountService." + identifier,
        identifier,
        suggested,
        1);
  }

  @Test
  void formatsNewTermsAndViolationsWithDetailLines() {
    var summary =
        formatter.formatSummary(
            3,
            List.of(
                violation("openAccountWithOverdraft", "openOverdraftAccount"),
                violation("accountWithOverdraft", "overdraftAccount")));

    assertThat(summary)
        .isEqualTo(
            """
            Vocabulary: 3 new terms harvested, 2 deprecated synonyms in use
              openAccountWithOverdraft → use openOverdraftAccount (billing: "overdraft account")
              accountWithOverdraft → use overdraftAccount (billing: "overdraft account")""");
  }

  @Test
  void usesSingularFormsWhenCountsAreOne() {
    var summary =
        formatter.formatSummary(1, List.of(violation("accountWithOverdraft", "overdraftAccount")));

    assertThat(summary).startsWith("Vocabulary: 1 new term harvested, 1 deprecated synonym in use");
  }

  @Test
  void omitsSynonymClauseWhenNoViolations() {
    assertThat(formatter.formatSummary(1, List.of())).isEqualTo("Vocabulary: 1 new term harvested");
    assertThat(formatter.formatSummary(0, List.of()))
        .isEqualTo("Vocabulary: 0 new terms harvested");
  }

  @Test
  void fallsBackToCanonicalTermWhenNoRenameSuggestionExists() {
    var summary = formatter.formatSummary(0, List.of(violation("legacyName", null)));

    assertThat(summary).contains("legacyName → use canonical term \"overdraft account\" (billing)");
  }

  @Test
  void rejectsInvalidArguments() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> formatter.formatSummary(-1, List.of()))
        .withMessageContaining("newTermCount");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> formatter.formatSummary(0, null))
        .withMessageContaining("violations");
  }
}
