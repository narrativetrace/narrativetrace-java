/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** Turns a euro amount into the amount a card is charged in its own currency. */
public interface CurrencyConverter {

  /**
   * Converts {@code euroCents} into {@code currency}, in that currency's minor units, rounded half
   * up to the minor unit.
   */
  int convert(int euroCents, String currency);
}
