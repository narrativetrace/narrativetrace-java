/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.glossary.fixtures.EnumStaticNearMissFixture;
import ai.narrativetrace.glossary.fixtures.SkipRuleNearMissFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The boundaries of the three rules that decide whether a scanned method is vocabulary.
 *
 * <p>INTENT: {@code GlossaryStaticScannerTest} pins each rule on the shape it exists to drop — a
 * data class's synthesized members, an enum's two compiler statics, a {@code values()} someone
 * named. The near misses were unpinned, and each of them is a shape test written as a conjunction:
 * drop any one conjunct and a method someone deliberately named disappears from the glossary
 * silently, which is the failure mode hardest to notice in a harvest that is additive by design.
 */
class StaticScannerSkipRuleBoundaryTest {

  private final GlossaryStaticScanner scanner = new GlossaryStaticScanner();

  @Test
  void aCopyWithNothingToCopyAndASiblingOfTheComponentPatternStayVocabulary() {
    assertThat(scannedMethodNames(SkipRuleNearMissFixture.class))
        .containsExactly("component1", "component1x", "copy", "copyFrom", "equals");
  }

  @Test
  void theSynthesizedShapesAndObjectsOwnMembersAreDropped() {
    assertThat(scannedMethodNames(SkipRuleNearMissFixture.class))
        .doesNotContain("component2", "toString", "packagePrivateMember");
  }

  @Test
  void anEnumsOwnValueOfOverloadIsVocabularyAndTheCompilersIsNot() {
    assertThat(parameterCountOf(EnumStaticNearMissFixture.class, "valueOf"))
        .as("the two-argument overload survives; the compiler's one-String one does not")
        .isEqualTo(2);
  }

  @Test
  void anEnumsInstanceMethodNamedValuesIsVocabularyAndTheCompilersStaticIsNot() {
    assertThat(parameterCountOf(EnumStaticNearMissFixture.class, "values"))
        .as("the instance overload survives; the compiler's no-argument static does not")
        .isEqualTo(1);
  }

  @Test
  void anEnumContributesBothOfItsOwnOverloadsAndNeitherCompilerStatic() {
    assertThat(scannedMethodNames(EnumStaticNearMissFixture.class))
        .containsExactly("valueOf", "values");
  }

  private List<String> scannedMethodNames(Class<?> type) {
    return nodesOf(type).stream().map(node -> node.signature().methodName()).sorted().toList();
  }

  private int parameterCountOf(Class<?> type, String methodName) {
    return nodesOf(type).stream()
        .map(TraceNode::signature)
        .filter(signature -> signature.methodName().equals(methodName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no node for " + methodName))
        .parameters()
        .size();
  }

  private List<TraceNode> nodesOf(Class<?> type) {
    return scanner.scan(List.of(type)).stream().flatMap(tree -> tree.roots().stream()).toList();
  }
}
