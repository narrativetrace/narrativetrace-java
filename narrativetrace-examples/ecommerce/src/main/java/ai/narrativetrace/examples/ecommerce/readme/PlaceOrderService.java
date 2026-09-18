/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce.readme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The README's "problem" example: a realistic order-placement method with hand-written logging
 * around business logic ({@link #placeOrder(OrderRequest)}), and the same method with the logging
 * deleted ({@link #placeOrderNarrated(OrderRequest)}). Kept in sync with the root {@code README.md}
 * by the {@code snippetCheck}/{@code snippetSync} Gradle tasks — each method's body is embedded
 * verbatim between a {@code // snippet:begin}/{@code // snippet:end} marker pair.
 */
public final class PlaceOrderService {

  private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

  private final CustomerService customers;
  private final CatalogService catalog;
  private final InventoryService inventory;
  private final PaymentService payments;
  private final OrderRepository orders;

  public PlaceOrderService(
      CustomerService customers,
      CatalogService catalog,
      InventoryService inventory,
      PaymentService payments,
      OrderRepository orders) {
    this.customers = customers;
    this.catalog = catalog;
    this.inventory = inventory;
    this.payments = payments;
    this.orders = orders;
  }

  // snippet:begin before
  public Order placeOrder(OrderRequest req) {
    log.info("Placing order {}", req.id());
    try {
      var customer = customers.find(req.id());
      var price = catalog.price(req.sku());
      inventory.reserve(req.sku(), req.qty());
      var payment = payments.charge(price);
      var order = orders.save(customer, payment);
      log.info("Order succeeded {}", order.id());
      return order;
    } catch (Exception ex) {
      log.error("Placing order failed {}", req.id(), ex);
      throw ex;
    }
  }

  // snippet:end before

  // snippet:begin after
  public Order placeOrderNarrated(OrderRequest req) {
    var customer = customers.find(req.id());
    var price = catalog.price(req.sku());
    inventory.reserve(req.sku(), req.qty());
    var payment = payments.charge(price);
    return orders.save(customer, payment);
  }
  // snippet:end after
}
