/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/PaymentService.java
package com.example.checkout;

/** Charges a card. The parameter is named {@code card}, not {@code cardNumber}, on purpose. */
public interface PaymentService {

  /**
   * @return the payment id the gateway issued
   * @throws PaymentDeclinedException when the gateway declines the card
   */
  String charge(String customerId, int amountCents, String card);
}
