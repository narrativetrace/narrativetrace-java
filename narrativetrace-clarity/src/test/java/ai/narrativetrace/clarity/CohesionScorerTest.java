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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CohesionScorerTest {

  private final CohesionScorer scorer = new CohesionScorer();

  @Test
  void scoresFullyAlignedClassHighly() {
    var methods = List.of("findById", "save", "deleteAll");
    double score = scorer.scoreClass("OrderRepository", methods);
    assertThat(score).isCloseTo(1.0, within(0.01));
  }

  @Test
  void scoresMisalignedMethodsLow() {
    var methods = List.of("findById", "render", "dispatch");
    double score = scorer.scoreClass("OrderRepository", methods);
    assertThat(score).isLessThan(0.5);
  }

  @Test
  void scoresBroadRoleGenerously() {
    var methods = List.of("calculateTotal", "validateOrder", "processPayment");
    double score = scorer.scoreClass("OrderService", methods);
    assertThat(score).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void scoresUnknownRoleNeutral() {
    var methods = List.of("addItem", "removeItem");
    double score = scorer.scoreClass("ShoppingCart", methods);
    assertThat(score).isCloseTo(0.7, within(0.01));
  }

  @Test
  void averagesAcrossMultipleClasses() {
    var classMethods =
        Map.of(
            "OrderRepository", List.of("findById", "save", "deleteAll"),
            "ShoppingCart", List.of("addItem", "removeItem"));
    double score = scorer.scoreTrace(classMethods);
    // Repository = 1.0, ShoppingCart = 0.7 → average = 0.85
    assertThat(score).isCloseTo(0.85, within(0.01));
  }

  @Test
  void scoresNewRoleSuffixUsingExpectedVerbs() {
    var methods = List.of("evaluateRequest", "enforcePolicy", "applyRules");
    double score = scorer.scoreClass("AccessPolicy", methods);
    assertThat(score).isCloseTo(1.0, within(0.01));
  }

  @Test
  void emptyClassNameReturnsUnknownRoleScore() {
    double score = scorer.scoreClass("", List.of("findById"));
    assertThat(score).isCloseTo(0.7, within(0.01));
  }

  @Test
  void emptyMethodListReturnsUnknownRoleScore() {
    double score = scorer.scoreClass("OrderRepository", List.of());
    assertThat(score).isCloseTo(0.7, within(0.01));
  }

  @Test
  void emptyClassMethodsMapReturnsUnknownRoleScore() {
    double score = scorer.scoreTrace(Map.of());
    assertThat(score).isCloseTo(0.7, within(0.01));
  }

  @Test
  void isAlignedReturnsFalseForEmptyMethodName() {
    // Empty method name → tokens empty → isAligned returns false → aligned count = 0
    var methods = List.of("");
    double score = scorer.scoreClass("OrderRepository", methods);
    assertThat(score).isCloseTo(0.0, within(0.01));
  }
}
