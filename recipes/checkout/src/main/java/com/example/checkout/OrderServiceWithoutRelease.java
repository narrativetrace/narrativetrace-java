/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/OrderServiceWithoutRelease.java
package com.example.checkout;

/**
 * The same checkout with no release path: a declined charge leaves the reservation behind.
 *
 * <p>INTENT: Two recipe pages need this code to RUN, not to be described — {@code
 * documentation/recipes/help-me-debug-and-fix-this-bug.md} (the bug, before its fix) and {@code
 * documentation/recipes/review-a-refactoring-did-behaviour-change.md} (the refactoring that drops
 * the release call while every test stays green). It is kept beside {@link DefaultOrderService}
 * rather than reconstructed by a flag, so each page embeds real source and each run is a real run.
 *
 * <p><b>@llmNote</b> Never the implementation to copy: {@link DefaultOrderService} is. A trace
 * names the interface, not the class, so both render as {@code OrderService.placeOrder} — which is
 * exactly what makes one approved baseline comparable across the two.
 */
public final class OrderServiceWithoutRelease implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public OrderServiceWithoutRelease(
      InventoryService inventory, PaymentService payments, OrderRepository orders) {
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    inventory.reserve(sku, quantity);
    var amountCents = UNIT_PRICE_CENTS * quantity;
    payments.charge(customerId, amountCents, cardNumber);
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }
}
