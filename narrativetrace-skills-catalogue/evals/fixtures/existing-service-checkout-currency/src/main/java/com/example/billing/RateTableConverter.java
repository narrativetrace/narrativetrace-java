/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts at today's rate, keeping the amount exact until the last step. */
public class RateTableConverter implements CurrencyConverter {

  private final ExchangeRates rates;

  public RateTableConverter(ExchangeRates rates) {
    this.rates = rates;
  }

  @Override
  public int convert(int euroCents, String currency) {
    BigDecimal euros = BigDecimal.valueOf(euroCents, 2);
    BigDecimal converted = euros.multiply(rates.rateFor(currency));
    return converted.setScale(0, RoundingMode.HALF_UP).movePointRight(2).intValueExact();
  }
}
