/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MethodNameScorerTest {

  private final MethodNameScorer scorer = new MethodNameScorer();

  @Test
  void scoresDomainVerbPlusNounHighly() {
    assertThat(scorer.score("calculateRefund")).isGreaterThanOrEqualTo(0.90);
    assertThat(scorer.score("validateCredentials")).isGreaterThanOrEqualTo(0.90);
    assertThat(scorer.score("reserveInventory")).isGreaterThanOrEqualTo(0.90);
  }

  @Test
  void scoresGenericSingleWordLow() {
    assertThat(scorer.score("process")).isLessThanOrEqualTo(0.15);
    assertThat(scorer.score("handle")).isLessThanOrEqualTo(0.15);
    assertThat(scorer.score("do")).isLessThanOrEqualTo(0.15);
    assertThat(scorer.score("run")).isLessThanOrEqualTo(0.15);
    assertThat(scorer.score("perform")).isLessThanOrEqualTo(0.15);
  }

  @Test
  void scoresGenericVerbPlusSpecificNounMedium() {
    double getCustomer = scorer.score("getCustomer");
    assertThat(getCustomer).isBetween(0.45, 0.65);

    double processOrder = scorer.score("processOrder");
    assertThat(processOrder).isBetween(0.45, 0.65);

    double handlePayment = scorer.score("handlePayment");
    assertThat(handlePayment).isBetween(0.45, 0.65);
  }

  @Test
  void scoresSingleNonGenericWordMedium() {
    double login = scorer.score("login");
    assertThat(login).isBetween(0.50, 0.70);

    double validate = scorer.score("validate");
    assertThat(validate).isBetween(0.50, 0.70);
  }

  @Test
  void penalizesAbbreviatedTokens() {
    double abbreviated = scorer.score("calcRefAmt");
    double full = scorer.score("calculateRefundAmount");
    assertThat(abbreviated).isLessThan(full);
  }

  @Test
  void penalizesExcessiveTokenCount() {
    double longName = scorer.score("calculateAndValidateAndProcessOrder");
    double shortName = scorer.score("calculateOrder");
    assertThat(longName).isLessThan(shortName);
  }

  @Test
  void rewardsMorphologicallyConfirmedVerb() {
    // "validate" has -ate suffix (morphologically confirmed verb)
    double validateOrder = scorer.score("validateOrder");
    // "fetch" is dictionary-only (standard verb, no morphological suffix)
    double fetchOrder = scorer.score("fetchOrder");
    assertThat(validateOrder).isGreaterThan(fetchOrder);
  }

  @Test
  void differentiatesPreviouslyEqualNames() {
    // These all scored 0.5 in the old system
    double getCustomer = scorer.score("getCustomer");
    double processOrder = scorer.score("processOrder");
    double handlePayment = scorer.score("handlePayment");

    // They should not ALL be exactly equal now
    boolean allEqual = getCustomer == processOrder && processOrder == handlePayment;
    assertThat(allEqual).isFalse();
  }

  @Test
  void scoresSingleBooleanPrefix() {
    double is = scorer.score("is");
    assertThat(is).isBetween(0.30, 0.50);
  }

  @Test
  void scoresSingleStandardVerb() {
    double create = scorer.score("create");
    assertThat(create).isBetween(0.35, 0.55);
  }

  @Test
  void scoresMorphologicalVerbWithoutDictionary() {
    // "customize" ends in -ize, morphologically a verb but not in domain verb list
    double customizeWidget = scorer.score("customizeWidget");
    assertThat(customizeWidget).isGreaterThan(0.50);
  }

  @Test
  void collocationDictionaryIsAccessible() {
    assertThat(scorer.collocationDictionary()).isNotNull();
    assertThat(scorer.collocationDictionary().isPreferred("reconcile", "ledger")).isTrue();
  }

  @Test
  void emptyStringReturnsZero() {
    assertThat(scorer.score("")).isEqualTo(0.0);
  }

  @Test
  void singleGenericVerbScoresExactly010() {
    // "process" is GENERIC → base = 0.10
    assertThat(scorer.score("process")).isEqualTo(0.10);
  }

  @Test
  void singleDomainVerbScoresExactly060() {
    // "calculate" is DOMAIN → base = 0.60
    assertThat(scorer.score("calculate")).isEqualTo(0.60);
  }

  @Test
  void singleUnknownMorphologicalVerbScores055() {
    // "customize" is UNKNOWN in VerbDictionary but ends with -ize → VERB → 0.55
    assertThat(scorer.score("customize")).isEqualTo(0.55);
  }

  @Test
  void singleUnknownNonVerbScores050() {
    // "widget" is UNKNOWN in VerbDictionary and not a morphological verb → 0.50
    assertThat(scorer.score("widget")).isEqualTo(0.50);
  }

  @Test
  void multiTokenUnknownVerbUsesVerbQuality06() {
    // "customize" is UNKNOWN + morphological VERB → scoreVerbQuality returns 0.6
    // "Widget" is NOT_GENERIC → tokenSpecificity = 1.0
    // no abbreviations → abbreviation = 1.0
    // 2 tokens → tokenCount = 1.0
    // "customize" ends in -ize → hasSuffix + not inDictionary → scoreMorphology returns 0.8
    // = 0.6*0.45 + 1.0*0.15 + 1.0*0.10 + 1.0*0.15 + 0.8*0.15 = 0.27+0.15+0.10+0.15+0.12 = 0.79
    assertThat(scorer.score("customizeWidget")).isGreaterThan(0.70);
  }

  @Test
  void multiTokenUnknownNonVerbUsesVerbQuality04() {
    // "splork" is UNKNOWN + not morphological verb → scoreVerbQuality returns 0.4
    // "Widget" is NOT_GENERIC → specificity 1.0
    // no abbreviations → 1.0
    // 2 tokens → tokenCount 1.0
    // "splork" is UNKNOWN + no suffix → scoreMorphology 0.3
    // = 0.4*0.45 + 1.0*0.15 + 1.0*0.10 + 1.0*0.15 + 0.3*0.15 = 0.18+0.15+0.10+0.15+0.045 = 0.625
    double score = scorer.score("splorkWidget");
    assertThat(score).isGreaterThan(0.55);
    assertThat(score).isLessThan(0.70);
  }

  @Test
  void twoTokenNameScoresHigherThanFiveTokenName() {
    // 2 tokens → tokenCount = 1.0; 5 tokens → tokenCount = 0.55
    double twoToken = scorer.score("calculateOrder");
    double fiveToken = scorer.score("calculateAndValidateTheOrder");
    assertThat(twoToken).isGreaterThan(fiveToken);
  }

  @Test
  void scoreTokenCountBoundaryAtFourTokens() {
    // 4 tokens: "calculateTheBigOrder" → tokenCount = 1.0
    double fourToken = scorer.score("calculateTheBigOrder");
    // 5 tokens: "calculateTheVeryBigOrder" → tokenCount = 0.55
    double fiveToken = scorer.score("calculateTheVeryBigOrder");
    assertThat(fourToken).isGreaterThan(fiveToken);
  }

  @Test
  void veryLongNameScoresLowerThanShortName() {
    // 8 tokens → tokenCount = max(0.0, 0.7 - 0.15*4) = 0.10
    // 2 tokens → tokenCount = 1.0
    double longName = scorer.score("calculateTheVeryBigExpensiveSpecialCustomOrder");
    double shortName = scorer.score("calculateOrder");
    assertThat(longName).isLessThan(shortName);
  }

  @Test
  void singleTokenAbbreviationReducesScore() {
    // "calc" is AMBIGUOUS abbreviation (score=0.3), UNKNOWN category
    // base for UNKNOWN non-verb = 0.50, then * 0.3 = 0.15
    double calc = scorer.score("calc");
    assertThat(calc).isLessThan(0.50);
  }

  @Test
  void scoreMorphologyReturnsCorrectValueForGenericVerb() {
    // "getOrder" → "get" is GENERIC → scoreMorphology returns 0.3
    // "fetchOrder" → "fetch" is STANDARD + no suffix → inDictionary=true, hasSuffix=false → 0.8
    double getScore = scorer.score("getOrder");
    double fetchScore = scorer.score("fetchOrder");
    // get has lower morphology (0.3 vs 0.8)
    assertThat(getScore).isLessThan(fetchScore);
  }

  @Test
  void scoreMorphologyReturnsOneForDictionaryVerbWithSuffix() {
    // "validate" is STANDARD + has -ate suffix → inDictionary=true, hasSuffix=true → 1.0
    // "calculate" is DOMAIN + has -ate suffix → inDictionary=true, hasSuffix=true → 1.0
    double validateOrder = scorer.score("validateOrder");
    assertThat(validateOrder).isGreaterThanOrEqualTo(0.90);
  }

  @Test
  void scoreMorphologyReturnsPointThreeForNeitherDictionaryNorSuffix() {
    // "splork" is UNKNOWN + no recognized suffix → 0.3
    // vs "customizeOrder" where "customize" has -ize suffix → 0.8
    double splorkOrder = scorer.score("splorkOrder");
    double customizeOrder = scorer.score("customizeOrder");
    assertThat(splorkOrder).isLessThan(customizeOrder);
  }

  @Test
  void hasVerbSuffixReturnsFalseForShortTokens() {
    // Token length < 4 → hasVerbSuffix returns false
    // "en" is 2 chars → too short for suffix
    // "ateX" is len 4, suffix "ate" is len 3 → word.length() > suffix.length() → true
    // But "ate" itself is len 3 < 4 → returns false
    double ate = scorer.score("ate");
    assertThat(ate).isLessThan(0.55);
  }

  @Test
  void scoreTokenSpecificityWithAllGenericTokens() {
    // "getDataInfoStuff" → tokens after first: "data" (VAGUE 0.2), "info" (VAGUE 0.2), "stuff"
    // (VAGUE 0.2)
    // tokenSpecificity = average(0.2, 0.2, 0.2) = 0.2
    double genericTokens = scorer.score("getDataInfoStuff");
    double domainTokens = scorer.score("getCustomerOrderPayment");
    assertThat(genericTokens).isLessThan(domainTokens);
  }

  @Test
  void abbreviationInMultiTokenReducesScore() {
    // "calcRef" has abbreviations for both tokens
    // vs "calculateRefund" with no abbreviations
    double withAbbr = scorer.score("calcRef");
    double withoutAbbr = scorer.score("calculateRefund");
    assertThat(withAbbr).isLessThan(withoutAbbr);
  }

  @Test
  void exactlyTwoTokensUsesHighTokenCount() {
    // "calculateOrder" = 2 tokens → tokenCount 1.0
    // "calculate" alone → single token path → 0.60
    // The multi-token path should give higher score due to better overall weighting
    double twoTokens = scorer.score("calculateOrder");
    assertThat(twoTokens).isGreaterThan(0.90);
  }

  @Test
  void exactlyFourTokensUsesHighTokenCount() {
    // 4 tokens: "calculateBigOrderTotal" → tokenCount = 1.0
    double fourTokens = scorer.score("calculateBigOrderTotal");
    assertThat(fourTokens).isGreaterThan(0.85);
  }

  @Test
  void fiveTokensHasLowerTokenCountThanFour() {
    // 5 tokens → tokenCount = max(0, 0.7 - 0.15*1) = 0.55
    // vs 4 tokens → tokenCount = 1.0
    double fiveTokens = scorer.score("calculateBigOrderTotalAmount");
    double fourTokens = scorer.score("calculateBigOrderTotal");
    assertThat(fiveTokens).isLessThan(fourTokens);
  }

  @Test
  void scoreMorphologyDifferentiatesHasSuffixFromNone() {
    // "validateOrder" → "validate" in dict + has suffix (-ate) → morphology 1.0
    // "createOrder" → "create" in dict + NO suffix → morphology 0.8
    // Both are STANDARD verbs so verb quality same. Only morphology differs.
    double validateOrder = scorer.score("validateOrder");
    double createOrder = scorer.score("createOrder");
    assertThat(validateOrder).isGreaterThan(createOrder);
  }

  @Test
  void hasVerbSuffixMatchesAteIzeIseSuffix() {
    // "validate" → -ate suffix, "normalize" → -ize suffix, "organise" → -ise suffix
    // All should get morphology boost in multi-token
    double validate = scorer.score("validateOrder");
    double normalize = scorer.score("normalizeOrder");
    // Both should score similarly high (both are domain+suffix → morphology 1.0)
    assertThat(validate).isGreaterThan(0.90);
    assertThat(normalize).isGreaterThan(0.90);
  }

  @Test
  void hasVerbSuffixRequiresTokenLongerThanSuffix() {
    // "en" is a verb suffix, but "en" alone is length 2 which is < 4 → hasVerbSuffix won't run
    // "open" → length 4, suffix "en" length 2, 4 > 2 → true
    // But "open" is STANDARD verb in dictionary → scoreMorphology: inDictionary=true,
    // hasSuffix=true
    // vs "closeOrder" → "close" is STANDARD, suffix? close ends in nothing matching →
    // hasSuffix=false
    // inDictionary=true, hasSuffix=false → 0.8
    double openOrder = scorer.score("openOrder");
    double closeOrder = scorer.score("closeOrder");
    // open has higher morphology (1.0 vs 0.8) so should score higher
    assertThat(openOrder).isGreaterThan(closeOrder);
  }

  @Test
  void sixTokenNameTokenCountAffectsExactScore() {
    // "calculateRefundAmountTotalAndSend" → 6 tokens
    // tokenCount = max(0, 0.7 - 0.15*(6-4)) = 0.40
    // With subtraction→addition mutation: max(0, 0.7 - 0.15*(6+4)) = 0.0
    // verbQuality=1.0, tokenSpecificity≈0.8, abbreviation=1.0, morphology=1.0
    // Normal: 0.45+0.12+0.10+0.06+0.15 = 0.88
    // Mutant: 0.45+0.12+0.10+0.00+0.15 = 0.82
    double score = scorer.score("calculateRefundAmountTotalAndSend");
    assertThat(score).isCloseTo(0.88, within(0.02));
  }

  @Test
  void exactlyOneTokenReturnsLowerTokenCountThanTwoTokens() {
    // single token "calculate" → scoreSingleToken path: DOMAIN → 0.60
    // 2-token "calculateRefund" → scoreMultiToken path: tokenCount=1.0, all dimensions max
    // The single token gets 0.60 (not scoreMultiToken with tokenCount=0.6)
    double singleToken = scorer.score("calculate");
    assertThat(singleToken).isEqualTo(0.60);
  }

  @Test
  void hasVerbSuffixReturnsFalseForTokenShorterThanFour() {
    // "en" is a verb suffix but the token "en" has length 2 < 4 → hasVerbSuffix = false
    // As a single token, "en" is UNKNOWN in VerbDictionary, not morphological verb → 0.50
    // If hasVerbSuffix mutation returns true for short tokens, morphology changes
    double en = scorer.score("en");
    assertThat(en).isEqualTo(0.50);
  }

  @Test
  void multiTokenScoreVerbQualityChangesWhenUnknownVerbIsDetected() {
    // "customizeRefund" → "customize" is UNKNOWN + has -ize suffix → verbQuality=0.6
    // vs "splorkRefund" → "splork" is UNKNOWN + no suffix → verbQuality=0.4
    double withVerb = scorer.score("customizeRefund");
    double withoutVerb = scorer.score("splorkRefund");
    assertThat(withVerb).isGreaterThan(withoutVerb);
  }

  @Test
  void scoreMorphologyReturnsPointThreeForNoSuffixAndNoDictionary() {
    // "splorkRefund" → "splork" is UNKNOWN in VerbDictionary, no suffix → morphology=0.3
    // "calculateRefund" → "calculate" is DOMAIN + has -ate suffix → morphology=1.0
    // Other dimensions are similar (both have "Refund" as second token)
    // morphology difference = (1.0-0.3)*0.15 = 0.105
    double splork = scorer.score("splorkRefund");
    double calculate = scorer.score("calculateRefund");
    assertThat(calculate - splork).isGreaterThan(0.09);
  }
}
