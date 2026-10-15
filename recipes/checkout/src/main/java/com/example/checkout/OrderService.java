/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/OrderService.java
package com.example.checkout;

/** Places one order: reserve the stock, charge the card, save the order. */
public interface OrderService {

  /**
   * @return the id of the order that was saved
   * @throws PaymentDeclinedException when the card is declined
   */
  String placeOrder(String customerId, String sku, int quantity, String cardNumber);
}
