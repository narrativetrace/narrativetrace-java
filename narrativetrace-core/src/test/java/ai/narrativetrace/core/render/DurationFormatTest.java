/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * One formatter for every surface that prints a duration. Integer division was reporting {@code 0}
 * for anything under a millisecond, which is most of a unit-test trace — the tracer unable to say
 * anything about the speed of the code it traced.
 */
class DurationFormatTest {

  @Test
  void wholeMillisecondsStayWhole() {
    assertThat(DurationFormat.millis(412_000_000L)).isEqualTo("412");
  }

  @Test
  void subMillisecondCallsKeepMicrosecondResolution() {
    assertThat(DurationFormat.millis(999_000L)).isEqualTo("0.999");
    assertThat(DurationFormat.millis(870_000L)).isEqualTo("0.87");
  }

  @Test
  void trailingZerosAreStripped() {
    assertThat(DurationFormat.millis(1_500_000L)).isEqualTo("1.5");
    assertThat(DurationFormat.millis(2_100_000L)).isEqualTo("2.1");
  }

  @Test
  void anythingBelowAMicrosecondRoundsToZero() {
    assertThat(DurationFormat.millis(400L)).isEqualTo("0");
    assertThat(DurationFormat.millis(0L)).isEqualTo("0");
  }

  @Test
  void roundsRatherThanTruncatesAtTheThirdDecimal() {
    assertThat(DurationFormat.millis(1_999_600L)).isEqualTo("2");
    assertThat(DurationFormat.millis(1_234_500L)).isEqualTo("1.235");
  }

  @Test
  void largeDurationsSurviveWithoutScientificNotation() {
    assertThat(DurationFormat.millis(3_600_000_000_000L)).isEqualTo("3600000");
  }

  @Test
  void formattingIsLocaleIndependent() {
    var previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY); // decimal comma
      assertThat(DurationFormat.millis(1_500_000L)).isEqualTo("1.5");
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void rejectsANegativeDuration() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> DurationFormat.millis(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
