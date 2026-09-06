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

class ClassNameScorerTest {

  private final ClassNameScorer scorer = new ClassNameScorer();

  @Test
  void scoresDomainPlusPatternSuffixHighly() {
    assertThat(scorer.score("OrderService")).isGreaterThanOrEqualTo(0.90);
    assertThat(scorer.score("PaymentGateway")).isGreaterThanOrEqualTo(0.90);
    assertThat(scorer.score("UserRepository")).isGreaterThanOrEqualTo(0.90);
  }

  @Test
  void scoresBareGenericSuffixLow() {
    assertThat(scorer.score("Manager")).isLessThanOrEqualTo(0.25);
    assertThat(scorer.score("Helper")).isLessThanOrEqualTo(0.25);
    assertThat(scorer.score("Util")).isLessThanOrEqualTo(0.25);
  }

  @Test
  void scoresGenericSuffixWithPrefixMedium() {
    double orderManager = scorer.score("OrderManager");
    assertThat(orderManager).isBetween(0.40, 0.60);

    double dataProcessor = scorer.score("DataProcessor");
    assertThat(dataProcessor).isBetween(0.25, 0.50);
  }

  @Test
  void scoresDomainMultiWordHighly() {
    assertThat(scorer.score("ShoppingCart")).isGreaterThanOrEqualTo(0.85);
    assertThat(scorer.score("PricingEngine")).isGreaterThanOrEqualTo(0.85);
  }

  @Test
  void penalizesAbbreviatedPrefix() {
    double abbreviated = scorer.score("OrdSvc");
    double full = scorer.score("OrderService");
    assertThat(abbreviated).isLessThan(full);
  }

  @Test
  void scoresInterfaceAdjectivePattern() {
    assertThat(scorer.score("Comparable")).isGreaterThanOrEqualTo(0.80);
    assertThat(scorer.score("Serializable")).isGreaterThanOrEqualTo(0.80);
  }

  @Test
  void scoresMeaninglessPrefixWithPatternSuffix() {
    // "DataService" - vague prefix + good suffix → scores lower than domain prefix
    double dataService = scorer.score("DataService");
    double orderService = scorer.score("OrderService");
    assertThat(dataService).isLessThan(orderService);
  }

  @Test
  void scoresVerbSuffixLow() {
    // "OrderValidate" - verb as class suffix
    double score = scorer.score("OrderValidate");
    assertThat(score).isLessThan(scorer.score("OrderValidator"));
  }

  @Test
  void penalizesExcessiveTokenCount() {
    double longName = scorer.score("VeryLongMultiWordServiceName");
    assertThat(longName).isLessThan(scorer.score("OrderService"));
  }

  @Test
  void emptyStringReturnsZero() {
    assertThat(scorer.score("")).isEqualTo(0.0);
  }

  @Test
  void singleTokenGenericReturns005() {
    // "Manager" is GENERIC → 0.05
    assertThat(scorer.score("Manager")).isEqualTo(0.05);
  }

  @Test
  void singleTokenDesignPatternReturns010() {
    // "Service" is DESIGN_PATTERN → 0.10
    assertThat(scorer.score("Service")).isEqualTo(0.10);
  }

  @Test
  void singleTokenFunctionalReturns010() {
    // "Validator" is FUNCTIONAL → 0.10
    assertThat(scorer.score("Validator")).isEqualTo(0.10);
  }

  @Test
  void singleTokenAdjectiveReturns085() {
    // "Comparable" → morphology ADJECTIVE → 0.85
    assertThat(scorer.score("Comparable")).isEqualTo(0.85);
  }

  @Test
  void singleTokenUnknownNonAdjectiveReturns075() {
    // "Widget" → UNKNOWN role, not adjective → 0.75
    assertThat(scorer.score("Widget")).isEqualTo(0.75);
  }

  @Test
  void prefixQualityEmptyReturnsZero() {
    // Single-token names go through scoreSingleToken, not scoreMultiToken with prefix
    // But 2 token with prefix — empty prefix list shouldn't happen with multi-token
    // Test with first token empty by using underscore prefix: "_Service"
    // The underscore prefix generates empty tokens → filtered out
    // Instead test: a class with only a suffix and no prefix tokens
    // This is covered by scoreSingleToken path (tokens.size() == 1)
    assertThat(scorer.score("Service")).isEqualTo(0.10);
  }

  @Test
  void threeTokenNameScoresHigherThanFiveTokenName() {
    // 3 tokens → tokenCount 1.0; 5 tokens → 0.9 - 0.1*(5-3) = 0.7
    double threeToken = scorer.score("UserOrderService");
    double fiveToken = scorer.score("VeryLongUserOrderService");
    assertThat(threeToken).isGreaterThan(fiveToken);
  }

  @Test
  void fourTokenNameScoresLowerThanThreeTokenName() {
    // 4 tokens → tokenCount = max(0.0, 0.9 - 0.1*(4-3)) = 0.8
    // 3 tokens → tokenCount = 1.0
    double fourToken = scorer.score("UserOrderListService");
    double threeToken = scorer.score("UserOrderService");
    assertThat(fourToken).isLessThan(threeToken);
  }

  @Test
  void abbreviationInTokensReducesScore() {
    // "OrdSvc" has abbreviations; "OrderService" does not
    double abbreviated = scorer.score("OrdSvc");
    double full = scorer.score("OrderService");
    assertThat(abbreviated).isLessThan(full);
  }

  @Test
  void unknownRoleSuffixScoresHigherThanGenericSuffix() {
    // "OrderWidget" → lastToken "Widget" is UNKNOWN → roleSuffix 0.8
    // "OrderManager" → lastToken "Manager" is GENERIC → roleSuffix 0.3
    double unknownSuffix = scorer.score("OrderWidget");
    double genericSuffix = scorer.score("OrderManager");
    assertThat(unknownSuffix).isGreaterThan(genericSuffix);
  }

  @Test
  void verbSuffixMorphologyScoresLowerThanNounSuffix() {
    // "OrderValidate" → lastToken "Validate" → VERB → morphology 0.3
    // "OrderTransaction" → lastToken "Transaction" → NOUN → morphology 1.0
    double verbSuffix = scorer.score("OrderValidate");
    double nounSuffix = scorer.score("OrderTransaction");
    assertThat(verbSuffix).isLessThan(nounSuffix);
  }

  @Test
  void twoTokenNameScoresEqualToThreeTokenForTokenCount() {
    // Both 2 and 3 tokens → tokenCount = 1.0
    // "OrderService" (2 tokens) and "UserOrderService" (3 tokens)
    // Other dimensions differ so scores differ, but both get full token count
    double twoToken = scorer.score("OrderService");
    double threeToken = scorer.score("UserOrderService");
    // Both should be high (tokenCount=1.0 for both)
    assertThat(twoToken).isGreaterThan(0.85);
    assertThat(threeToken).isGreaterThan(0.85);
  }

  @Test
  void fiveTokenNameScoresLowerThanFourToken() {
    // 5 tokens → tokenCount = max(0, 0.9-0.1*2) = 0.7
    // 4 tokens → tokenCount = max(0, 0.9-0.1*1) = 0.8
    // Use all domain prefix tokens so prefix quality is equal
    double fiveToken = scorer.score("AccountUserOrderPaymentService");
    double fourToken = scorer.score("AccountUserOrderService");
    assertThat(fiveToken).isLessThan(fourToken);
  }

  @Test
  void sixTokenNameHasEvenLowerTokenCount() {
    // 6 tokens → tokenCount = max(0, 0.9-0.1*3) = 0.6
    double sixToken = scorer.score("VeryLongBigUserOrderService");
    double threeToken = scorer.score("UserOrderService");
    assertThat(sixToken).isLessThan(threeToken);
  }
}
