/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class DomainVocabularyPropertyTest {

  @Property
  void everyRetainedTokenAnswersForItsOwnSetAndNoOther(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String verb,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String noun) {
    var vocabulary = DomainVocabulary.of(Set.of(verb), Set.of(noun));

    assertThat(vocabulary.isDomainVerb(verb)).isTrue();
    assertThat(vocabulary.isDomainNoun(noun)).isTrue();
    assertThat(vocabulary.isAcceptedAbbreviation(verb))
        .as("declaring a term never accepts it as shorthand — only the abbreviations section does")
        .isFalse();
    assertThat(vocabulary.isAcceptedAbbreviation(noun)).isFalse();
  }

  @Property
  void everyDeclaredAbbreviationIsAcceptedAndSpelledOut(
      @ForAll @AlphaChars @StringLength(min = 1, max = 8) String abbreviation,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String expansion) {
    var vocabulary = DomainVocabulary.of(Set.of(), Set.of(), Map.of(abbreviation, expansion));

    assertThat(vocabulary.isAcceptedAbbreviation(abbreviation)).isTrue();
    assertThat(vocabulary.expansionOf(abbreviation)).isEqualTo(expansion);
  }

  @Property
  void constructionIsIdempotent(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String term) {
    var once = DomainVocabulary.of(Set.of(term), Set.of(), Map.of(term, "expansion"));
    var twice = DomainVocabulary.of(once.verbs(), once.nouns(), once.abbreviations());

    assertThat(twice).isEqualTo(once);
  }

  @Property
  void multiWordPhrasesNeverMatchAToken(
      @ForAll @AlphaChars @StringLength(min = 1, max = 10) String head,
      @ForAll @AlphaChars @StringLength(min = 1, max = 10) String tail) {
    var vocabulary = DomainVocabulary.of(Set.of(), Set.of(head + " " + tail));

    assertThat(vocabulary.isEmpty()).isTrue();
    assertThat(vocabulary.isDomainNoun(head)).isFalse();
    assertThat(vocabulary.isDomainNoun(tail)).isFalse();
  }
}
