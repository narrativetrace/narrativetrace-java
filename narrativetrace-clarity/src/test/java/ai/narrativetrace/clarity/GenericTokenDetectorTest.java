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

class GenericTokenDetectorTest {

  private final GenericTokenDetector detector = new GenericTokenDetector();

  @Test
  void detectsMeaninglessSingleLetter() {
    var result = detector.detect("x");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
    assertThat(result.score()).isEqualTo(0.0);
  }

  @Test
  void detectsMeaninglessPlaceholder() {
    var result = detector.detect("foo");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
    assertThat(result.score()).isEqualTo(0.0);
  }

  @Test
  void detectsVagueWord() {
    var result = detector.detect("data");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
    assertThat(result.score()).isEqualTo(0.2);
  }

  @Test
  void detectsTypedGeneric() {
    var result = detector.detect("count");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.TYPED_GENERIC);
    assertThat(result.score()).isEqualTo(0.5);
  }

  @Test
  void returnsNotGenericForDomainWord() {
    var result = detector.detect("customer");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.NOT_GENERIC);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void isCaseInsensitive() {
    var result = detector.detect("Data");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
    assertThat(result.score()).isEqualTo(0.2);
  }

  @Test
  void detectsModernVaguePlaceholders() {
    assertThat(detector.detect("meta").tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
    assertThat(detector.detect("blob").tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
    assertThat(detector.detect("dataset").tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
  }

  @Test
  void detectsInfraTypedGenerics() {
    assertThat(detector.detect("path").tier()).isEqualTo(GenericTokenDetector.Tier.TYPED_GENERIC);
    assertThat(detector.detect("url").tier()).isEqualTo(GenericTokenDetector.Tier.TYPED_GENERIC);
    assertThat(detector.detect("token").tier()).isEqualTo(GenericTokenDetector.Tier.TYPED_GENERIC);
    assertThat(detector.detect("tenant").tier()).isEqualTo(GenericTokenDetector.Tier.TYPED_GENERIC);
  }

  @Test
  void detectsNewMeaninglessPlaceholder() {
    var result = detector.detect("todo");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
    assertThat(result.score()).isEqualTo(0.0);
  }

  @Test
  void singleLetterAIsMeaningless() {
    // 'a' is the lower boundary
    assertThat(detector.detect("a").tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
  }

  @Test
  void singleLetterZIsMeaningless() {
    // 'z' is the upper boundary
    assertThat(detector.detect("z").tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
  }

  @Test
  void singleDigitIsNotMeaningless() {
    // '0' is not in a-z range → not meaningless single letter
    // But it's also not in any word list → NOT_GENERIC
    var result = detector.detect("0");
    assertThat(result.tier()).isNotEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
  }

  @Test
  void twoCharStringIsNotSingleLetterMeaningless() {
    // "ab" is length 2 → isMeaninglessSingleLetter returns false
    // "ab" is not in any word list → NOT_GENERIC
    var result = detector.detect("ab");
    assertThat(result.tier()).isEqualTo(GenericTokenDetector.Tier.NOT_GENERIC);
  }

  @Test
  void treatsProjectDeclaredNounAsDomainSpecific() {
    var projectDetector =
        new GenericTokenDetector(DomainVocabulary.of(Set.of(), Set.of("position", "record")));

    assertThat(projectDetector.detect("position").tier())
        .isEqualTo(GenericTokenDetector.Tier.NOT_GENERIC);
    assertThat(projectDetector.detect("position").score()).isEqualTo(1.0);
    assertThat(projectDetector.detect("record").tier())
        .isEqualTo(GenericTokenDetector.Tier.NOT_GENERIC);
  }

  @Test
  void refusesToRescueMeaninglessPlaceholdersDeclaredByAProject() {
    var projectDetector =
        new GenericTokenDetector(DomainVocabulary.of(Set.of(), Set.of("temp", "foo")));

    assertThat(projectDetector.detect("temp").tier())
        .isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
    assertThat(projectDetector.detect("foo").tier())
        .isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
  }

  @Test
  void refusesToRescueSingleLettersDeclaredByAProject() {
    var projectDetector = new GenericTokenDetector(DomainVocabulary.of(Set.of(), Set.of("x")));

    assertThat(projectDetector.detect("x").tier()).isEqualTo(GenericTokenDetector.Tier.MEANINGLESS);
  }

  @Test
  void declaredVerbsAreNotNouns() {
    var projectDetector = new GenericTokenDetector(DomainVocabulary.of(Set.of("data"), Set.of()));

    assertThat(projectDetector.detect("data").tier()).isEqualTo(GenericTokenDetector.Tier.VAGUE);
  }

  @Test
  void projectNounsMatchCaseInsensitively() {
    var projectDetector =
        new GenericTokenDetector(DomainVocabulary.of(Set.of(), Set.of("Tranche")));

    assertThat(projectDetector.detect("TRANCHE").tier())
        .isEqualTo(GenericTokenDetector.Tier.NOT_GENERIC);
  }

  @Test
  void rejectsNullVocabulary() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GenericTokenDetector(null));
  }
}
