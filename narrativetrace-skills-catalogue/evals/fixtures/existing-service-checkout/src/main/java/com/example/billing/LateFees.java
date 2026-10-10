/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** What a late invoice costs, by days overdue. */
public final class LateFees {

  private static final int CENTS_PER_DAY = 150;

  private LateFees() {}

  public static int feeFor(int daysLate) {
    if (daysLate <= 0) {
      return 0;
    }
    return daysLate * CENTS_PER_DAY;
  }
}
