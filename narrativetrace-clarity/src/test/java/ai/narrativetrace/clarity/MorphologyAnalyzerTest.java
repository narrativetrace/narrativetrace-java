/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MorphologyAnalyzerTest {

  private final MorphologyAnalyzer analyzer = new MorphologyAnalyzer();

  @Test
  void detectsVerbBySuffix() {
    assertThat(analyzer.analyze("validate").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
    assertThat(analyzer.analyze("normalize").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
    assertThat(analyzer.analyze("notify").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
    assertThat(analyzer.analyze("flatten").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void detectsNounBySuffix() {
    assertThat(analyzer.analyze("transaction").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
    assertThat(analyzer.analyze("payment").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
    assertThat(analyzer.analyze("security").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
    assertThat(analyzer.analyze("performance").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
    assertThat(analyzer.analyze("handler").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
    assertThat(analyzer.analyze("repository").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);
  }

  @Test
  void detectsAdjectiveBySuffix() {
    assertThat(analyzer.analyze("readable").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.ADJECTIVE);
    assertThat(analyzer.analyze("active").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.ADJECTIVE);
    assertThat(analyzer.analyze("synchronous").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.ADJECTIVE);
    assertThat(analyzer.analyze("optional").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.ADJECTIVE);
    assertThat(analyzer.analyze("dynamic").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.ADJECTIVE);
  }

  @Test
  void returnsUnknownForAmbiguous() {
    assertThat(analyzer.analyze("cart").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
    assertThat(analyzer.analyze("shop").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
  }

  @Test
  void handlesShortWords() {
    assertThat(analyzer.analyze("up").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
    assertThat(analyzer.analyze("in").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
  }

  @Test
  void prefersVerbDictionaryOverSuffix() {
    // "action" ends with -tion (noun suffix) but if it were in VerbDictionary, verb wins.
    // Since "action" is NOT in VerbDictionary, it stays NOUN by suffix.
    assertThat(analyzer.analyze("action").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.NOUN);

    // "calculate" is a verb by both dictionary and suffix — should be VERB
    assertThat(analyzer.analyze("calculate").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void detectsVerbBySuffixWhenNotInDictionary() {
    // "customize" ends in -ize, not in VerbDictionary, but morphologically a verb
    assertThat(analyzer.analyze("customize").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void verbDictionaryWordReturnVerb() {
    // "calculate" is in VerbDictionary as DOMAIN → verbResult.category() != UNKNOWN → VERB
    assertThat(analyzer.analyze("calculate").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
    // "get" is in VerbDictionary as GENERIC → still != UNKNOWN → VERB
    assertThat(analyzer.analyze("get").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void exactlyFourCharWordWithSuffixDetectedCorrectly() {
    // "open" is length 4, MIN_SUFFIX_LENGTH is 4 → passes the < 4 check
    // But "open" doesn't have a matching suffix (no verb/noun/adj suffix match)
    // Actually "open" ends in "en" which is a verb suffix, but length must be > suffix length
    // "open".length() = 4, "en".length() = 2, 4 > 2 → true → VERB (if not already in dict)
    // But "open" IS in VerbDictionary (STANDARD) → returns VERB from dictionary check
    assertThat(analyzer.analyze("open").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void threeCharWordReturnUnknown() {
    // "xyz" is length 3 < MIN_SUFFIX_LENGTH (4) → UNKNOWN
    // But first check: is "xyz" in VerbDictionary? No → UNKNOWN category
    // Then length < 4 → returns UNKNOWN
    assertThat(analyzer.analyze("xyz").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
  }

  @Test
  void suffixLengthBoundaryInMatchesSuffix() {
    // matchesSuffix requires word.length() > suffix.length()
    // "ate" has length 3 = suffix "ate" length 3 → 3 > 3 is false → no match
    // But "ate" is length 3 < MIN_SUFFIX_LENGTH (4) → skips suffix check
    // "vate" has length 4, suffix "ate" length 3 → 4 > 3 → true → VERB
    // "vate" is not in VerbDictionary → falls through to suffix check
    assertThat(analyzer.analyze("vate").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.VERB);
  }

  @Test
  void shortWordWithAdjectiveSuffixReturnsUnknown() {
    // "led" is length 3 < MIN_SUFFIX_LENGTH (4) → UNKNOWN
    // Without the MIN_SUFFIX_LENGTH guard, "led" ends in "ed" (adjective suffix)
    // and 3 > 2 → would match ADJECTIVE. Guard prevents this.
    assertThat(analyzer.analyze("led").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
  }

  @Test
  void wordEqualLengthToSuffixDoesNotMatch() {
    // "tion" has length 4 = suffix "tion" length 4
    // matchesSuffix requires word.length() > suffix.length() → 4 > 4 is false
    // If boundary mutation changes to >=, "tion" would match as NOUN
    // But with >, "tion" should be UNKNOWN (no suffix matches with strict >)
    assertThat(analyzer.analyze("tion").partOfSpeech())
        .isEqualTo(MorphologyAnalyzer.PartOfSpeech.UNKNOWN);
  }
}
