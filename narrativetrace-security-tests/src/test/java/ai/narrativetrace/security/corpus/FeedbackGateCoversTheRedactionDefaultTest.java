/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.tooling.feedback.ValueFreeCheck;
import ai.narrativetrace.tooling.feedback.ValueFreeRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * "Reuse the runtime's deny-list and value shapes, never copy them" — kept by an assertion rather
 * than by a Java dependency, because there cannot be one.
 *
 * <p>INTENT: {@code narrativetrace-tooling} declares zero dependencies and its own architecture
 * gate fails the build on any reference into {@code ai.narrativetrace.core..} — the CLI's {@code
 * java -jar} archive carries only that library's classes. So the value-free gate restates the
 * vocabulary and the shapes, and THIS module, the only one that may see both, holds them together:
 * every name and every value the renderer redacts by default must also be refused by the gate. A
 * term dropped from one side and not the other fails here, in {@code check}, instead of in a public
 * issue.
 *
 * <p><b>@llmNote</b> The implication is asserted in ONE direction, and the asymmetry is the design.
 * The renderer refuses an entropy heuristic and verifies every national-id checksum because a false
 * positive there silently blanks a user's data; the gate uses entropy and matches id SHAPES because
 * a false positive here is a refusal that names its rule. So the gate is a strict superset, and
 * {@link #theGateIsStricterThanTheRendererAndThatIsTheDesign()} pins that it really is strict —
 * otherwise a future simplification could quietly make the two equal and lose the entropy half.
 */
class FeedbackGateCoversTheRedactionDefaultTest {

  /** A canary that no rule of the gate reacts to on its own — so a rejection is about the NAME. */
  private static final String NEUTRAL_VALUE = "ada";

  static List<RedactionCase> deniedNames() {
    return HostileCorpus.redactions().stream()
        .filter(row -> "redacted".equals(row.expect()))
        .filter(row -> row.name() != null)
        .toList();
  }

  static List<RedactionCase> secretShapedValues() {
    return HostileCorpus.redactions().stream()
        .filter(row -> "redacted".equals(row.expect()))
        .filter(row -> row.value() != null)
        .toList();
  }

  @Test
  void theCanaryThisTestBindsIsItselfFilable() {
    assertThat(ValueFreeCheck.rulesRefusing(NEUTRAL_VALUE))
        .as("a neutral value must pass, or every name row below would pass for the wrong reason")
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("deniedNames")
  void everyNameTheRendererRedactsIsAlsoRefusedByTheGate(RedactionCase row) {
    assertThat(RedactionPolicy.DEFAULT.shouldRedact(row.name()))
        .as("%s: the runtime must redact this name, or the corpus row is stale", row.id())
        .isTrue();

    assertThat(ValueFreeCheck.rulesRefusing(row.name() + ": " + NEUTRAL_VALUE))
        .as("%s: the gate must refuse \"%s\" carrying a value", row.id(), row.name())
        .contains(ValueFreeRule.NAMED_SECRET);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("secretShapedValues")
  void everyValueShapeTheRendererRedactsIsAlsoRefusedByTheGate(RedactionCase row) {
    assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(row.value()))
        .as("%s: the runtime must redact this value shape, or the corpus row is stale", row.id())
        .isTrue();

    assertThat(ValueFreeCheck.rulesRefusing(row.value()))
        .as("%s: the gate must refuse the value shape \"%s\"", row.id(), row.value())
        .isNotEmpty();
  }

  @Test
  void theRedactionMarkerIsTheSameStringOnBothSides() {
    assertThat(ValueFreeCheck.rulesRefusing("placeOrder returned " + RedactionPolicy.MARKER))
        .as("the gate's marker rule must key on the runtime's own marker literal")
        .contains(ValueFreeRule.MARKER);
  }

  /**
   * The gate refuses a checksum-failing lookalike the renderer deliberately leaves VISIBLE. Both
   * decisions are right for their own surface, and this test is what stops the next person from
   * "fixing" the disagreement.
   */
  @Test
  void theGateIsStricterThanTheRendererAndThatIsTheDesign() {
    String badCheckDigits = "52998224726";

    assertThat(RedactionPolicy.DEFAULT.shouldRedactValue(badCheckDigits))
        .as("the renderer leaves a checksum-failing lookalike visible, by ruling")
        .isFalse();
    assertThat(ValueFreeCheck.rulesRefusing(badCheckDigits))
        .as(
            "the gate refuses it anyway: an unfilable report costs a sentence, a public id does"
                + " not")
        .contains(ValueFreeRule.VALUE_SHAPE);
  }
}
