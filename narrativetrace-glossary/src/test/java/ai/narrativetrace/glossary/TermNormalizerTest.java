/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TermNormalizerTest {

  private final TermNormalizer normalizer = new TermNormalizer();

  @Test
  void convergesCamelPascalAndSnakeCaseToOnePhrase() {
    assertThat(normalizer.phrase("accountWithOverdraft")).isEqualTo("account with overdraft");
    assertThat(normalizer.phrase("AccountWithOverdraft")).isEqualTo("account with overdraft");
    assertThat(normalizer.phrase("account_with_overdraft")).isEqualTo("account with overdraft");
  }

  @Test
  void singularizesPluralNouns() {
    assertThat(normalizer.phrase("overdraftAccounts")).isEqualTo("overdraft account");
    assertThat(normalizer.phrase("entries")).isEqualTo("entry");
    assertThat(normalizer.phrase("addresses")).isEqualTo("address");
    assertThat(normalizer.phrase("taxBoxes")).isEqualTo("tax box");
  }

  @Test
  void keepsNonPluralTrailingSWords() {
    assertThat(normalizer.phrase("status")).isEqualTo("status");
    assertThat(normalizer.phrase("analysis")).isEqualTo("analysis");
    assertThat(normalizer.phrase("progress")).isEqualTo("progress");
  }

  @Test
  void keepsSFinalSingularAndInvariantWords() {
    assertThat(normalizer.phrase("alias")).isEqualTo("alias");
    assertThat(normalizer.phrase("gas")).isEqualTo("gas");
    assertThat(normalizer.phrase("series")).isEqualTo("series");
    assertThat(normalizer.phrase("atlas")).isEqualTo("atlas");
    assertThat(normalizer.phrase("bias")).isEqualTo("bias");
    assertThat(normalizer.phrase("canvas")).isEqualTo("canvas");
    assertThat(normalizer.phrase("chaos")).isEqualTo("chaos");
    assertThat(normalizer.phrase("lens")).isEqualTo("lens");
    assertThat(normalizer.phrase("news")).isEqualTo("news");
    assertThat(normalizer.phrase("species")).isEqualTo("species");
    assertThat(normalizer.phrase("alwaysRetry")).isEqualTo("always retry");
  }

  @Test
  void singularizesSePluralsByStrippingOnlyTheFinalS() {
    assertThat(normalizer.phrase("cases")).isEqualTo("case");
    assertThat(normalizer.phrase("responses")).isEqualTo("response");
    assertThat(normalizer.phrase("phases")).isEqualTo("phase");
    assertThat(normalizer.phrase("clauses")).isEqualTo("clause");
    assertThat(normalizer.phrase("purposes")).isEqualTo("purpose");
    assertThat(normalizer.phrase("houses")).isEqualTo("house");
    assertThat(normalizer.phrase("promises")).isEqualTo("promise");
  }

  @Test
  void singularizesEsPluralsWhoseStemKeepsItsTrailingS() {
    assertThat(normalizer.phrase("gases")).isEqualTo("gas");
    assertThat(normalizer.phrase("aliases")).isEqualTo("alias");
    assertThat(normalizer.phrase("lenses")).isEqualTo("lens");
    assertThat(normalizer.phrase("statuses")).isEqualTo("status");
    assertThat(normalizer.phrase("buses")).isEqualTo("bus");
  }

  @Test
  void keepsThirdPersonVerbTokensUntouched() {
    assertThat(normalizer.phrase("containsDuplicates")).isEqualTo("contains duplicate");
    assertThat(normalizer.phrase("matchesRules")).isEqualTo("matches rule");
  }

  @Test
  void pluralSuffixRulesRequireAStemBeforeTheSuffix() {
    assertThat(normalizer.phrase("ies")).isEqualTo("ie");
    assertThat(normalizer.phrase("xes")).isEqualTo("xe");
  }

  @Test
  void neverSingularizesStopwords() {
    assertThat(normalizer.phrase("markedAsDone")).isEqualTo("marked as done");
    assertThat(normalizer.phrase("chargeWasApplied")).isEqualTo("charge was applied");
  }

  @Test
  void splitsVerbMethodIntoVerbPhraseAndObjectNounPhrase() {
    assertThat(normalizer.methodCandidates("openAccountWithOverdraft"))
        .containsExactly(
            new TermNormalizer.Candidate("open account with overdraft", TermKind.VERB_PHRASE),
            new TermNormalizer.Candidate("account with overdraft", TermKind.NOUN_PHRASE));
  }

  @Test
  void singleWordObjectBecomesWordCandidate() {
    assertThat(normalizer.methodCandidates("chargeCards"))
        .containsExactly(
            new TermNormalizer.Candidate("charge card", TermKind.VERB_PHRASE),
            new TermNormalizer.Candidate("card", TermKind.WORD));
  }

  @Test
  void bareVerbMethodYieldsOnlyVerbPhrase() {
    assertThat(normalizer.methodCandidates("charge"))
        .containsExactly(new TermNormalizer.Candidate("charge", TermKind.VERB_PHRASE));
  }

  @Test
  void nonVerbMethodYieldsNounCandidate() {
    assertThat(normalizer.methodCandidates("overdraftLimit"))
        .containsExactly(new TermNormalizer.Candidate("overdraft limit", TermKind.NOUN_PHRASE));
  }

  @Test
  void objectPhraseDropsLeadingStopwords() {
    assertThat(normalizer.methodCandidates("checkForDuplicates"))
        .containsExactly(
            new TermNormalizer.Candidate("check for duplicate", TermKind.VERB_PHRASE),
            new TermNormalizer.Candidate("duplicate", TermKind.WORD));
  }

  @Test
  void parameterCandidateStripsTrailingIdRole() {
    assertThat(normalizer.parameterCandidate("overdraftAccountId"))
        .contains(new TermNormalizer.Candidate("overdraft account", TermKind.NOUN_PHRASE));
    assertThat(normalizer.parameterCandidate("customerIds"))
        .contains(new TermNormalizer.Candidate("customer", TermKind.WORD));
    assertThat(normalizer.parameterCandidate("id")).isEmpty();
  }

  @Test
  void classCandidateStripsRecognizedRoleSuffix() {
    assertThat(normalizer.classCandidate("OverdraftService"))
        .contains(new TermNormalizer.Candidate("overdraft", TermKind.WORD));
    assertThat(normalizer.classCandidate("PaymentPlanRepository"))
        .contains(new TermNormalizer.Candidate("payment plan", TermKind.NOUN_PHRASE));
    assertThat(normalizer.classCandidate("InvoiceLineItem"))
        .contains(new TermNormalizer.Candidate("invoice line item", TermKind.NOUN_PHRASE));
    assertThat(normalizer.classCandidate("Service")).isEmpty();
  }

  @Test
  void exceptionCandidateStripsExceptionAndErrorSuffixes() {
    assertThat(normalizer.exceptionCandidate("InsufficientFundsException"))
        .contains(new TermNormalizer.Candidate("insufficient fund", TermKind.NOUN_PHRASE));
    assertThat(normalizer.exceptionCandidate("TimeoutError"))
        .contains(new TermNormalizer.Candidate("timeout", TermKind.WORD));
    assertThat(normalizer.exceptionCandidate("Exception")).isEmpty();
  }

  @Test
  void rejectsBlankIdentifier() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.phrase(" "))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.phrase(null))
        .withMessageContaining("identifier");
  }

  /**
   * Regression: {@code __} is not blank, so it passed the guard, and the tokenizer reads no word in
   * it — so {@code phrase} produced the empty string and tripped its own postcondition, an {@code
   * AssertionError} raised inside the user's test run. The `Optional`-returning normalizations
   * indexed an empty list instead. Both reachable through {@code GlossaryHarvester}, because {@code
   * __} is a legal Java identifier. Found by the security suite's generated identifiers.
   */
  @Test
  void anIdentifierWithNoReadableWordIsRefusedRatherThanErasedByPhrase() {
    assertThatThrownBy(() -> normalizer.phrase("__"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one readable word");
  }

  @Test
  void anIdentifierWithNoReadableWordIsRefusedRatherThanErasedByMethodCandidates() {
    assertThatThrownBy(() -> normalizer.methodCandidates("___"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one readable word");
  }

  @Test
  void anIdentifierWithNoReadableWordYieldsNoParameterCandidate() {
    assertThat(normalizer.parameterCandidate("__")).isEmpty();
  }

  @Test
  void anIdentifierWithNoReadableWordYieldsNoClassCandidate() {
    assertThat(normalizer.classCandidate("__")).isEmpty();
  }

  @Test
  void anIdentifierWithNoReadableWordYieldsNoExceptionCandidate() {
    assertThat(normalizer.exceptionCandidate("__")).isEmpty();
  }

  @Test
  void aBlankIdentifierIsStillRefusedByItsOwnMessage() {
    assertThatThrownBy(() -> normalizer.phrase(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identifier must not be blank");
  }

  /**
   * The {@code __} fix above pinned the instance, not the class. An identifier that mixes a
   * separator with whitespace is not blank, so the blank guard lets it through, and the tokenizer
   * used to hand back the whitespace itself as a word — which is a readable word to every check
   * here and to none in reality. Same erasure, one layer down; found by the security suite again.
   */
  @ParameterizedTest
  @ValueSource(strings = {"_ ", " _", "__ ", "_ _", "_\t", "\n_"})
  void aSeparatorMixedWithWhitespaceCarriesNoReadableWordEither(String identifier) {
    assertThat(identifier).as("the blank guard must not be what refuses these").isNotBlank();

    assertThatThrownBy(() -> normalizer.phrase(identifier))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one readable word");
  }

  @Test
  void whitespaceAroundAWordIsNotItselfPartOfThePhrase() {
    assertThat(normalizer.phrase("account_ ")).isEqualTo("account");
    assertThat(normalizer.phrase("_ account")).isEqualTo("account");
  }
}
