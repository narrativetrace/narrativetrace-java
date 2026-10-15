/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/DefaultOrderService.java
package com.example.checkout;

/** The checkout: reserve the stock, charge the card, save the order — and release on a decline. */
public final class DefaultOrderService implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public DefaultOrderService(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    try {
      payments.charge(customerId, amountCents, cardNumber);
    } catch (PaymentDeclinedException declined) {
      inventory.release(sku, quantity);
      throw declined;
    }
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
