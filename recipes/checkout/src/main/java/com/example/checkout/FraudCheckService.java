/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/FraudCheckService.java
package com.example.checkout;

/** Scores an order before it is charged. Slow enough that a checkout runs it off its own thread. */
public interface FraudCheckService {

  /**
   * @return true when the order may be charged
   */
  boolean approve(String customerId, int amountCents);
}
