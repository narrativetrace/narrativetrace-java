/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The one normaliser every context export goes through. */
class ContextExportTest {

  @Test
  @DisplayName("null stays null, so an absent field stays absent")
  void nullStaysNull() {
    assertThat(ContextExport.normalized((String) null)).isNull();
    assertThat(ContextExport.normalized((Object) null)).isNull();
  }

  @Test
  @DisplayName("an ordinary value is returned unchanged")
  void anOrdinaryValueIsUnchanged() {
    assertThat(ContextExport.normalized("/orders/42")).isEqualTo("/orders/42");
    assertThat(ContextExport.normalized("")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\nb", "a\rb", "a\tb", "a\u001bb", "a\u0000b", "a\u0085b"})
  @DisplayName("no control character survives")
  void noControlCharacterSurvives(String raw) {
    var normalized = ContextExport.normalized(raw);

    assertThat(normalized.chars().anyMatch(Character::isISOControl)).isFalse();
  }

  @Test
  @DisplayName("a value at the cap is untouched, one past it is truncated")
  void theCapIsABoundary() {
    var atCap = "x".repeat(ContextExport.MAX_LENGTH);
    var pastCap = "x".repeat(ContextExport.MAX_LENGTH + 1);

    assertThat(ContextExport.normalized(atCap)).isEqualTo(atCap);
    assertThat(ContextExport.normalized(pastCap))
        .hasSize(ContextExport.MAX_LENGTH + 1)
        .endsWith("…");
  }

  @Test
  @DisplayName("a huge value is bounded, not merely shortened")
  void aHugeValueIsBounded() {
    assertThat(ContextExport.normalized("x".repeat(1_000_000)))
        .hasSize(ContextExport.MAX_LENGTH + 1);
  }

  @Test
  @DisplayName("escaping runs before the cap, so an escape cannot straddle the boundary")
  void escapingRunsBeforeTheCap() {
    var raw = "\n".repeat(1_000);

    var normalized = ContextExport.normalized(raw);

    assertThat(normalized).doesNotContain("\n").hasSize(ContextExport.MAX_LENGTH + 1);
  }

  @Test
  @DisplayName("a typed context value is normalised through its toString")
  void aTypedValueIsNormalisedThroughToString() {
    var route = ai.narrativetrace.api.event.HttpRoute.of("/orders\nforged");

    assertThat(ContextExport.normalized(route)).doesNotContain("\n").startsWith("/orders");
  }
}
