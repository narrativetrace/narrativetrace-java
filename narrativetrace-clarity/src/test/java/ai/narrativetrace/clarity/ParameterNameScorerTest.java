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

class ParameterNameScorerTest {

  private final ParameterNameScorer scorer = new ParameterNameScorer();

  @Test
  void scoresDomainSpecificParamHighly() {
    assertThat(scorer.score("customerId")).isGreaterThanOrEqualTo(0.85);
    assertThat(scorer.score("orderTotal")).isGreaterThanOrEqualTo(0.85);
    assertThat(scorer.score("shippingAddress")).isGreaterThanOrEqualTo(0.85);
  }

  @Test
  void scoresMeaninglessParamLow() {
    assertThat(scorer.score("x")).isLessThanOrEqualTo(0.05);
    assertThat(scorer.score("data")).isLessThanOrEqualTo(0.20);
    assertThat(scorer.score("obj")).isLessThanOrEqualTo(0.20);
    assertThat(scorer.score("tmp")).isLessThanOrEqualTo(0.20);
  }

  @Test
  void scoresTypedGenericMedium() {
    double count = scorer.score("count");
    assertThat(count).isBetween(0.40, 0.60);

    double name = scorer.score("name");
    assertThat(name).isBetween(0.40, 0.60);
  }

  @Test
  void penalizesAbbreviatedParam() {
    double abbreviated = scorer.score("custId");
    double full = scorer.score("customerId");
    assertThat(abbreviated).isLessThan(full);
  }

  @Test
  void scoresCompoundDomainParamHighly() {
    assertThat(scorer.score("shippingAddress")).isGreaterThanOrEqualTo(0.90);
    assertThat(scorer.score("accountBalance")).isGreaterThanOrEqualTo(0.85);
  }

  @Test
  void scoresNotGenericAbbreviatedSingleToken() {
    // "ctx" is NOT_GENERIC in GenericTokenDetector but is a known abbreviation
    double ctx = scorer.score("ctx");
    assertThat(ctx).isBetween(0.30, 0.50);
  }

  @Test
  void penalizesAmbiguousAbbreviationInCompound() {
    // "procId" has ambiguous abbreviation "proc"
    double procId = scorer.score("procId");
    double processId = scorer.score("processId");
    assertThat(procId).isLessThan(processId);
  }

  @Test
  void emptyStringReturnsZero() {
    assertThat(scorer.score("")).isEqualTo(0.0);
  }

  @Test
  void multiTokenWithMeaninglessReturnsFifteen() {
    // "fooBar" → "foo" is MEANINGLESS → hasMeaningless = true → returns 0.15
    assertThat(scorer.score("fooBar")).isCloseTo(0.15, within(0.01));
  }

  @Test
  void multiTokenWithDomainTokenGetsBonus() {
    // "customerAmount" → "customer" NOT_GENERIC, "amount" TYPED_GENERIC
    // hasDomainToken = true → domainBonus = 1.0
    double withDomain = scorer.score("customerAmount");
    // "statusCount" → "status" TYPED_GENERIC, "count" TYPED_GENERIC
    // hasDomainToken = false → domainBonus = 0.7
    double withoutDomain = scorer.score("statusCount");
    assertThat(withDomain).isGreaterThan(withoutDomain);
  }

  @Test
  void multiTokenWithUniversalAbbreviationScoresHigh() {
    // "customerId" → "customer" NOT_GENERIC, "id" UNIVERSAL abbreviation (score 1.0)
    double customerId = scorer.score("customerId");
    assertThat(customerId).isGreaterThanOrEqualTo(0.85);
  }

  @Test
  void multiTokenAbbreviationScoringDistinguishesTiers() {
    // "custId" → "cust" AMBIGUOUS (0.5), "id" UNIVERSAL (1.0) → avg 0.75
    // "customerId" → "customer" no abbr (1.0), "id" UNIVERSAL (1.0) → avg 1.0
    double ambiguous = scorer.score("custId");
    double full = scorer.score("customerId");
    assertThat(ambiguous).isLessThan(full);
  }

  @Test
  void multiTokenWithNoDomainTokenUsesLowerBonus() {
    // "statusCount" → "status" TYPED_GENERIC, "count" TYPED_GENERIC
    // hasDomainToken = false → domainBonus = 0.7
    // avgGeneric: status=0.9 (TYPED_GENERIC mapped to 0.9), count=0.9 → avg=0.9
    // abbreviation: no abbreviations → 1.0
    // score = 0.9*0.45 + 1.0*0.25 + 0.7*0.30 = 0.405+0.25+0.21 = 0.865
    double score = scorer.score("statusCount");
    assertThat(score).isCloseTo(0.865, within(0.02));
  }

  @Test
  void multiTokenWithAllDomainTokensUsesFullBonus() {
    // "customerRegion" → "customer" NOT_GENERIC (1.0), "region" NOT_GENERIC (1.0)
    // hasDomainToken = true → domainBonus = 1.0
    // avgGeneric: 1.0, 1.0 → avg=1.0
    // abbreviation: no abbreviations → 1.0
    // score = 1.0*0.45 + 1.0*0.25 + 1.0*0.30 = 1.0
    double score = scorer.score("customerRegion");
    assertThat(score).isCloseTo(1.0, within(0.02));
  }

  @Test
  void multiTokenNoAbbreviationsReturnsFullAbbreviationScore() {
    // "customerRegion" → no abbreviations in either token → scoreAbbreviations = 1.0
    // If mutation replaces the 1.0 return with 0.0, abbreviation drops dramatically
    double score = scorer.score("customerRegion");
    // With abbreviation=1.0: 1.0*0.45 + 1.0*0.25 + 1.0*0.30 = 1.0
    // With abbreviation=0.0: 1.0*0.45 + 0.0*0.25 + 1.0*0.30 = 0.75
    assertThat(score).isEqualTo(1.0);
  }

  @Test
  void multiTokenWithoutMeaninglessDoesNotReturnFifteen() {
    // "customerRegion" has no MEANINGLESS tokens → hasMeaningless=false → normal scoring
    // Score should be much higher than 0.15
    double score = scorer.score("customerRegion");
    assertThat(score).isGreaterThan(0.85);
  }
}
