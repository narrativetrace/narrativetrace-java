/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * B-206, the numeric question: a number's own text is shape-scanned like a string's.
 *
 * <p>INTENT: a 16-digit card number held as a {@code long} is the realistic leak — the value-shape
 * axis asks what the bytes say, and the bytes of {@code 4111111111111111} say card number whatever
 * the Java type holding them. Only a Luhn-valid number of 12 or more digits is withheld; a number
 * that fails the checksum, or is shorter, prints as before.
 */
class ValueRendererNumericLeafSecretTest {

  private final ValueRenderer renderer = new ValueRenderer();

  private void assertWithheld(Object number) {
    assertThat(renderer.render(number)).isEqualTo("[REDACTED]");
    assertThat(renderer.renderStructured(number))
        .isEqualTo(new RenderedValue.StringVal("[REDACTED]"));
  }

  @Test
  void aLongHoldingACardNumberIsWithheldOnBothPaths() {
    assertWithheld(4111111111111111L);
  }

  @Test
  void aBigIntegerHoldingACardNumberIsWithheldOnBothPaths() {
    assertWithheld(new BigInteger("4111111111111111"));
  }

  @Test
  void aBigDecimalHoldingACardNumberIsWithheldOnBothPaths() {
    assertWithheld(new BigDecimal("4111111111111111"));
  }

  @Test
  void aLongThatFailsTheChecksumStaysVisible() {
    assertThat(renderer.render(4111111111111112L)).isEqualTo("4111111111111112");
    assertThat(renderer.renderStructured(4111111111111112L))
        .isEqualTo(new RenderedValue.LongVal(4111111111111112L));
    assertThat(renderer.render(1_700_000_000_001L)).isEqualTo("1700000000001");
  }

  @Test
  void ordinaryNumbersAreUntouched() {
    assertThat(renderer.render(42)).isEqualTo("42");
    assertThat(renderer.render(3.5d)).isEqualTo("3.5");
    assertThat(renderer.render(new BigDecimal("12.50"))).isEqualTo("12.50");
    assertThat(renderer.renderStructured(42L)).isEqualTo(new RenderedValue.LongVal(42L));
  }
}
