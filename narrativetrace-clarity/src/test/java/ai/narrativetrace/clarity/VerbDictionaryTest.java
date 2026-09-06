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

import java.util.Set;
import org.junit.jupiter.api.Test;

class VerbDictionaryTest {

  private final VerbDictionary dictionary = new VerbDictionary();

  @Test
  void categorizeDomainSpecificVerb() {
    var result = dictionary.categorize("calculate");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void categorizeStandardVerb() {
    var result = dictionary.categorize("create");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.STANDARD);
    assertThat(result.score()).isEqualTo(0.8);
  }

  @Test
  void categorizeGenericVerb() {
    var result = dictionary.categorize("process");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.GENERIC);
    assertThat(result.score()).isEqualTo(0.4);
  }

  @Test
  void categorizeBooleanPrefix() {
    var result = dictionary.categorize("is");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.BOOLEAN_PREFIX);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void categorizesNewDomainVerb() {
    var result = dictionary.categorize("order");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void categorizesFinanceDomainVerb() {
    var result = dictionary.categorize("disburse");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void categorizesGamingDomainVerb() {
    var result = dictionary.categorize("spawn");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void returnsUnknownForUnrecognizedWord() {
    var result = dictionary.categorize("quux");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.UNKNOWN);
  }

  @Test
  void isCaseInsensitive() {
    var result = dictionary.categorize("Calculate");
    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void categorizesProgrammingVerbs() {
    assertThat(dictionary.categorize("deserialize").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(dictionary.categorize("instantiate").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void categorizesApiPlatformVerbs() {
    assertThat(dictionary.categorize("deprecate").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(dictionary.categorize("document").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void categorizesObservabilityVerbs() {
    assertThat(dictionary.categorize("mitigate").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(dictionary.categorize("postmortem").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void categorizesMlAiVerbs() {
    assertThat(dictionary.categorize("train").category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(dictionary.categorize("threshold").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(dictionary.categorize("explain").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void promotesProjectDeclaredVerbToDomain() {
    var projectDictionary =
        new VerbDictionary(DomainVocabulary.of(Set.of("fold"), Set.of("tranche")));

    var result = projectDictionary.categorize("fold");

    assertThat(result.category()).isEqualTo(VerbDictionary.Category.DOMAIN);
    assertThat(result.score()).isEqualTo(1.0);
    assertThat(dictionary.categorize("fold").category()).isEqualTo(VerbDictionary.Category.UNKNOWN);
  }

  @Test
  void promotesProjectDeclaredVerbOverStandard() {
    var projectDictionary = new VerbDictionary(DomainVocabulary.of(Set.of("send"), Set.of()));

    assertThat(projectDictionary.categorize("send").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void refusesToPromoteGenericVerbsDeclaredByAProject() {
    var projectDictionary =
        new VerbDictionary(DomainVocabulary.of(Set.of("process", "handle"), Set.of()));

    assertThat(projectDictionary.categorize("process").category())
        .isEqualTo(VerbDictionary.Category.GENERIC);
    assertThat(projectDictionary.categorize("handle").category())
        .isEqualTo(VerbDictionary.Category.GENERIC);
  }

  @Test
  void refusesToPromoteBooleanPrefixesDeclaredByAProject() {
    var projectDictionary = new VerbDictionary(DomainVocabulary.of(Set.of("is"), Set.of()));

    assertThat(projectDictionary.categorize("is").category())
        .isEqualTo(VerbDictionary.Category.BOOLEAN_PREFIX);
  }

  @Test
  void declaredNounsAreNotVerbs() {
    var projectDictionary = new VerbDictionary(DomainVocabulary.of(Set.of(), Set.of("tranche")));

    assertThat(projectDictionary.categorize("tranche").category())
        .isEqualTo(VerbDictionary.Category.UNKNOWN);
  }

  @Test
  void projectVerbsMatchCaseInsensitively() {
    var projectDictionary = new VerbDictionary(DomainVocabulary.of(Set.of("Fold"), Set.of()));

    assertThat(projectDictionary.categorize("FOLD").category())
        .isEqualTo(VerbDictionary.Category.DOMAIN);
  }

  @Test
  void rejectsNullVocabulary() {
    assertThatIllegalArgumentException().isThrownBy(() -> new VerbDictionary(null));
  }
}
