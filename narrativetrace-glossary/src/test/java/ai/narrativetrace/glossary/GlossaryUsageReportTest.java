/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryUsageReportTest {

  private final GlossaryUsageReport report = new GlossaryUsageReport();

  private static final GlossaryTerm NEW_TERM =
      new GlossaryTerm(
          "overdraft account",
          "billing",
          TermKind.NOUN_PHRASE,
          TermStatus.HARVESTED,
          null,
          Map.of(),
          List.of(),
          List.of("OverdraftService.open"),
          LocalDate.of(2026, 8, 11));

  private static final VocabularyViolation VIOLATION =
      new VocabularyViolation(
          "billing",
          "account with overdraft",
          "overdraft account",
          "AccountService.openAccountWithOverdraft",
          "openAccountWithOverdraft",
          "openOverdraftAccount",
          2);

  @Test
  void rendersNewTermsViolationsAndUsageDeterministically() {
    var harvest =
        new HarvestResult(
            List.of(
                new HarvestCandidate(
                    "billing", "overdraft account", TermKind.NOUN_PHRASE, "A.a", "x", 3),
                new HarvestCandidate(
                    "billing", "overdraft account", TermKind.NOUN_PHRASE, "B.b", "y", 1)));

    var json = report.render(harvest, List.of(NEW_TERM), List.of(VIOLATION));

    assertThat(json)
        .isEqualTo(
            """
            {
              "newTerms": [
                { "term": "overdraft account", "context": "billing" }
              ],
              "violations": [
                { "context": "billing", "alias": "account with overdraft", "canonicalTerm": "overdraft account", "site": "AccountService.openAccountWithOverdraft", "identifier": "openAccountWithOverdraft", "suggestedIdentifier": "openOverdraftAccount", "occurrences": 2 }
              ],
              "usage": [
                { "context": "billing", "phrase": "overdraft account", "occurrences": 4 }
              ]
            }
            """);
  }

  @Test
  void rendersEmptySectionsAsEmptyArrays() {
    var json = report.render(new HarvestResult(List.of()), List.of(), List.of());

    assertThat(json)
        .isEqualTo(
            """
            {
              "newTerms": [],
              "violations": [],
              "usage": []
            }
            """);
  }

  @Test
  void omitsSuggestedIdentifierWhenAbsent() {
    var withoutSuggestion =
        new VocabularyViolation(
            "billing",
            "account with overdraft",
            "overdraft account",
            "A.legacy",
            "legacy",
            null,
            1);

    var json = report.render(new HarvestResult(List.of()), List.of(), List.of(withoutSuggestion));

    assertThat(json).doesNotContain("suggestedIdentifier");
  }

  @Test
  void writesReportFileCreatingParentDirectories(@TempDir Path tempDir) throws Exception {
    var file = tempDir.resolve("narrativetrace/glossary-usage.json");

    report.write(file, new HarvestResult(List.of()), List.of(), List.of());

    assertThat(Files.readString(file)).startsWith("{\n  \"newTerms\": []");
  }

  @Test
  void rejectsNullArguments() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> report.render(null, List.of(), List.of()))
        .withMessageContaining("harvest");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> report.render(new HarvestResult(List.of()), null, List.of()))
        .withMessageContaining("newTerms");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> report.render(new HarvestResult(List.of()), List.of(), null))
        .withMessageContaining("violations");
  }
}
