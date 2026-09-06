/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainVocabularyTest {

  @Test
  void emptyVocabularyKnowsNothing() {
    var vocabulary = DomainVocabulary.empty();

    assertThat(vocabulary.isEmpty()).isTrue();
    assertThat(vocabulary.isDomainVerb("fold")).isFalse();
    assertThat(vocabulary.isDomainNoun("tranche")).isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation("fx")).isFalse();
    assertThat(vocabulary.expansionOf("fx")).isNull();
  }

  @Test
  void recognizesDeclaredVerbsAndNouns() {
    var vocabulary = DomainVocabulary.of(Set.of("fold"), Set.of("tranche"));

    assertThat(vocabulary.isEmpty()).isFalse();
    assertThat(vocabulary.isDomainVerb("fold")).isTrue();
    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
    assertThat(vocabulary.isDomainVerb("tranche")).isFalse();
    assertThat(vocabulary.isDomainNoun("fold")).isFalse();
  }

  @Test
  void onlyTheDeclaredAbbreviationsSectionMakesATokenAcceptedShorthand() {
    var vocabulary = DomainVocabulary.of(Set.of("calc"), Set.of("fx"), Map.of("acc", "account"));

    assertThat(vocabulary.isAcceptedAbbreviation("acc")).isTrue();
    assertThat(vocabulary.expansionOf("acc")).isEqualTo("account");
    assertThat(vocabulary.isAcceptedAbbreviation("calc"))
        .as("a token declared as a verb is a verb, not accepted shorthand")
        .isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation("fx"))
        .as("a token declared as a noun is a noun, not accepted shorthand")
        .isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation("mgr")).isFalse();
  }

  @Test
  void acceptedShorthandIsNotAlsoADomainVerbOrNoun() {
    var vocabulary = DomainVocabulary.of(Set.of(), Set.of(), Map.of("fx", "foreign exchange"));

    assertThat(vocabulary.isEmpty()).isFalse();
    assertThat(vocabulary.isDomainVerb("fx")).isFalse();
    assertThat(vocabulary.isDomainNoun("fx")).isFalse();
  }

  @Test
  void dropsAMultiWordAbbreviationKeyThatCouldNeverMatchAToken() {
    var vocabulary =
        DomainVocabulary.of(Set.of(), Set.of(), Map.of("f x", "foreign exchange", " ", "blank"));

    assertThat(vocabulary.abbreviations()).isEmpty();
  }

  @Test
  void matchesRegardlessOfCase() {
    var vocabulary = DomainVocabulary.of(Set.of("Fold"), Set.of("Tranche"));

    assertThat(vocabulary.isDomainVerb("FOLD")).isTrue();
    assertThat(vocabulary.isDomainNoun("tranche")).isTrue();
  }

  @Test
  void matchesAcceptedShorthandRegardlessOfCase() {
    var vocabulary = DomainVocabulary.of(Set.of(), Set.of(), Map.of("FX", "foreign exchange"));

    assertThat(vocabulary.isAcceptedAbbreviation("Fx")).isTrue();
    assertThat(vocabulary.expansionOf("fX")).isEqualTo("foreign exchange");
  }

  @Test
  void ignoresBlankAndMultiWordEntries() {
    var vocabulary = DomainVocabulary.of(Set.of("fold ", " "), Set.of("credit tranche", ""));

    assertThat(vocabulary.isDomainVerb("fold")).isTrue();
    assertThat(vocabulary.isDomainNoun("credit tranche")).isFalse();
    assertThat(vocabulary.isDomainNoun("credit")).isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation("")).isFalse();
  }

  @Test
  void rejectsNullTokenSets() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> DomainVocabulary.of(null, Set.of()))
        .withMessageContaining("verbs");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> DomainVocabulary.of(Set.of(), null))
        .withMessageContaining("nouns");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> DomainVocabulary.of(Set.of(), Set.of(), null))
        .withMessageContaining("abbreviations");
  }

  @Test
  void rejectsNullLookups() {
    var vocabulary = DomainVocabulary.of(Set.of("fold"), Set.of("tranche"));

    assertThatIllegalArgumentException().isThrownBy(() -> vocabulary.isDomainVerb(null));
    assertThatIllegalArgumentException().isThrownBy(() -> vocabulary.isDomainNoun(null));
    assertThatIllegalArgumentException().isThrownBy(() -> vocabulary.isAcceptedAbbreviation(null));
    assertThatIllegalArgumentException().isThrownBy(() -> vocabulary.expansionOf(null));
  }

  @Test
  void isValueBasedRegardlessOfInputOrderOrCase() {
    var one = DomainVocabulary.of(Set.of("fold", "Settle"), Set.of("tranche"));
    var other = DomainVocabulary.of(Set.of("SETTLE", "fold"), Set.of("Tranche"));

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }
}
