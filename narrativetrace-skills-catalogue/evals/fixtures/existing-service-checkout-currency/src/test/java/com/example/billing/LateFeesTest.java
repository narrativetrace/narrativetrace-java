/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LateFeesTest {

  @Test
  void anInvoicePaidOnTimeCostsNothingExtra() {
    assertEquals(0, LateFees.feeFor(0));
  }

  @Test
  void eachDayLateAddsTheDailyFee() {
    assertEquals(450, LateFees.feeFor(3));
  }
}
