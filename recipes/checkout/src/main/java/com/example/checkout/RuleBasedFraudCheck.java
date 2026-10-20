/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/RuleBasedFraudCheck.java
package com.example.checkout;

/** One rule, so the scenario needs no risk engine: an order above the review ceiling is refused. */
public final class RuleBasedFraudCheck implements FraudCheckService {

  /** Orders at or above this total go to a human instead of to the gateway. */
  public static final int REVIEW_CEILING_CENTS = 50_000;

  @Override
  public boolean approve(String customerId, int amountCents) {
    return amountCents < REVIEW_CEILING_CENTS;
  }
}
