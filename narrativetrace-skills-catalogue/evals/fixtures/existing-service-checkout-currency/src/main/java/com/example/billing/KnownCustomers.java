/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.util.Map;

/** The customer records checkout reads; a card with no record is billed in euro. */
public class KnownCustomers implements CustomerDirectory {

  private static final Map<String, String> CARD_CURRENCIES =
      Map.of("C-1234", "EUR", "C-2041", "CHF", "C-3310", "GBP");

  @Override
  public String cardCurrency(String customerId) {
    return CARD_CURRENCIES.getOrDefault(customerId, "EUR");
  }
}
