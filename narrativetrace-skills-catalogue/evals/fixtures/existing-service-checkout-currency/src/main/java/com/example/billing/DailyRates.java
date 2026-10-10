/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.math.BigDecimal;
import java.util.Map;

/** The rate table finance publishes every morning. */
public class DailyRates implements ExchangeRates {

  private static final Map<String, BigDecimal> RATES =
      Map.of(
          "EUR", BigDecimal.ONE,
          "CHF", new BigDecimal("0.93"),
          "GBP", new BigDecimal("0.86"));

  @Override
  public BigDecimal rateFor(String currency) {
    BigDecimal rate = RATES.get(currency);
    if (rate == null) {
      throw new IllegalArgumentException("no rate for " + currency);
    }
    return rate;
  }
}
