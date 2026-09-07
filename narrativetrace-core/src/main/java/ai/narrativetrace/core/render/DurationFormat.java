/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place a duration becomes text.
 *
 * <p>INTENT: Every artifact — Markdown, canonical JSON, chapter export — prints milliseconds, and
 * each used to divide nanos by a million itself. Integer division reports {@code 0} for anything
 * under a millisecond, which is most calls in a unit-test trace, leaving the tracer unable to say
 * anything about the speed of the code it traced. Milliseconds stay the unit (the schemas declare
 * {@code number}, never integer, so fractions have always been legal); microsecond resolution is
 * kept, and whole values still print whole so existing artifacts do not churn.
 *
 * <p><b>@llmNote</b> Never format a duration anywhere else. Three surfaces each deriving their own
 * is exactly how the scenario duration came to mean four different things across the runtimes.
 */
public final class DurationFormat {

  /** Microsecond resolution: three decimal places on a millisecond. */
  private static final int SCALE = 3;

  private DurationFormat() {}

  /**
   * Formats nanoseconds as milliseconds: up to three decimals, trailing zeros stripped, always
   * plain notation and independent of the default locale.
   *
   * @param nanos a non-negative duration in nanoseconds
   */
  public static String millis(long nanos) {
    if (nanos < 0) {
      throw new IllegalArgumentException("Duration must not be negative: " + nanos);
    }
    var value =
        BigDecimal.valueOf(nanos)
            .movePointLeft(6)
            .setScale(SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    return value.scale() < 0 ? value.setScale(0).toPlainString() : value.toPlainString();
  }
}
