/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VocabularyViolationsTest {

  private static final Glossary GLOSSARY =
      new Glossary(
          1,
          Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
          List.of(
              new GlossaryTerm(
                  "overdraft account",
                  "billing",
                  TermKind.NOUN_PHRASE,
                  TermStatus.CURATED,
                  null,
                  Map.of(),
                  List.of(new SynonymAlias("account with overdraft", null)),
                  List.of(),
                  LocalDate.of(2020, 1, 1))));

  @Test
  void buildsViolationWithCanonicalTermAndRenameSuggestion() {
    var suppressed =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.NOUN_PHRASE,
            "AccountService.openAccountWithOverdraft",
            "openAccountWithOverdraft",
            2);

    var violations = VocabularyViolations.collect(GLOSSARY, List.of(suppressed));

    assertThat(violations)
        .containsExactly(
            new VocabularyViolation(
                "billing",
                "account with overdraft",
                "overdraft account",
                "AccountService.openAccountWithOverdraft",
                "openAccountWithOverdraft",
                "openOverdraftAccount",
                2));
  }

  @Test
  void aggregatesOccurrencesForSameIdentifierAtSameSite() {
    var first =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.NOUN_PHRASE,
            "A.open",
            "accountWithOverdraft",
            2);
    var second =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.WORD,
            "A.open",
            "accountWithOverdraft",
            3);

    var violations = VocabularyViolations.collect(GLOSSARY, List.of(first, second));

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).occurrences()).isEqualTo(5);
  }

  @Test
  void leavesSuggestionNullWhenNoContiguousAliasWindowExists() {
    var suppressed =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.NOUN_PHRASE,
            "A.legacyName",
            "legacyName",
            1);

    var violations = VocabularyViolations.collect(GLOSSARY, List.of(suppressed));

    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).suggestedIdentifier()).isNull();
  }

  @Test
  void sortsViolationsDeterministically() {
    var b =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.NOUN_PHRASE,
            "B.open",
            "accountWithOverdraft",
            1);
    var a =
        new HarvestCandidate(
            "billing",
            "account with overdraft",
            TermKind.NOUN_PHRASE,
            "A.open",
            "accountWithOverdraft",
            1);

    var violations = VocabularyViolations.collect(GLOSSARY, List.of(b, a));

    assertThat(violations)
        .extracting(VocabularyViolation::site)
        .containsExactly("A.open", "B.open");
  }

  @Test
  void rejectsSuppressedCandidateThatIsNotAnAlias() {
    var notAnAlias =
        new HarvestCandidate("billing", "fresh phrase", TermKind.WORD, "A.a", "freshPhrase", 1);

    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> VocabularyViolations.collect(GLOSSARY, List.of(notAnAlias)))
        .withMessageContaining("not an alias");
  }

  @Test
  void rejectsNullArguments() {
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> VocabularyViolations.collect(null, List.of()))
        .withMessageContaining("glossary");
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> VocabularyViolations.collect(GLOSSARY, null))
        .withMessageContaining("suppressed");
  }
}
