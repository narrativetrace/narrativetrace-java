/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.math.BigDecimal;

/** Today's exchange rates. */
public interface ExchangeRates {

  /** How many units of {@code currency} one euro buys today. */
  BigDecimal rateFor(String currency);
}
