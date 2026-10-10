/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RateTableConverterTest {

  private final CurrencyConverter converter = new RateTableConverter(new DailyRates());

  @Test
  void euroIsChargedAsIs() {
    assertEquals(4500, converter.convert(4500, "EUR"));
  }

  @Test
  void francsAtTodaysRate() {
    assertEquals(9300, converter.convert(10000, "CHF"));
  }

  @Test
  void poundsAtTodaysRate() {
    assertEquals(4300, converter.convert(5000, "GBP"));
  }

  @Test
  void anUnknownCurrencyIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> converter.convert(100, "XYZ"));
  }
}
