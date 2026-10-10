/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Adjacency and idempotence probes for {@link ControlEscape}. */
class ControlEscapeAdversarialP7Test {

  private static final String MONKEY = "\uD83D\uDC35";

  @Test
  void lineSeparatorsOnBothSidesOfAPairAreEscapedAndThePairSurvives() {
    assertThat(ControlEscape.sanitize("\u2028" + MONKEY + "\u2029"))
        .isEqualTo("\\u2028" + MONKEY + "\\u2029");
  }

  @Test
  void aHighSurrogateFollowedByALineSeparatorIsEscapedAsAUnit() {
    assertThat(ControlEscape.sanitize("\uD83D\u2028")).isEqualTo("\\ud83d\\u2028");
  }

  @Test
  void aLoneLowSurrogateAfterAPairIsEscapedAndThePairStays() {
    assertThat(ControlEscape.sanitize(MONKEY + "\uDC00")).isEqualTo(MONKEY + "\\udc00");
  }

  @Test
  void deleteAndC1ControlCharactersBecomeUnicodeEscapes() {
    assertThat(ControlEscape.sanitize("a\u007fb\u0085c")).isEqualTo("a\\u007fb\\u0085c");
  }

  @Test
  void emptyTextStaysEmpty() {
    assertThat(ControlEscape.sanitize("")).isEmpty();
  }

  @Test
  void sanitizingAnAlreadySanitizedMixedStringChangesNothingFurther() {
    var hostile = "a\u2028\uD800\n\u0085\uDC00" + MONKEY + "\\u2028\t\u001b[31m";
    var once = ControlEscape.sanitize(hostile);

    assertThat(ControlEscape.sanitize(once)).isEqualTo(once);
  }
}
