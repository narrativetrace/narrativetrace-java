/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.examples.ecommerce.CustomerService;
import ai.narrativetrace.examples.ecommerce.DiscountService;
import ai.narrativetrace.examples.ecommerce.InventoryService;
import ai.narrativetrace.examples.ecommerce.NotificationService;
import ai.narrativetrace.examples.ecommerce.PaymentService;
import ai.narrativetrace.examples.ecommerce.ProductCatalogService;
import ai.narrativetrace.examples.ecommerce.ShippingEstimateService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Composes the ecommerce example's reused domain interfaces (customer, catalog, inventory, payment,
 * discount, shipping, notification) into the multi-line-cart order flow the shop's web API needs.
 *
 * <p><b>@llmNote</b> The example's own {@code DefaultOrderService} is deliberately NOT reused here
 * — its {@code placeOrder(customerId, productId, quantity)} signature is single-line, with no
 * discount/shipping/notification step and no persistence. Composing the six interfaces directly is
 * the design that survived contact with the code; see README.md "reuse, never copy" for the full
 * note. Zero changes were needed in the example itself.
 */
public class OrderPlacementService {

  private static final Logger log = LoggerFactory.getLogger(OrderPlacementService.class);

  private final CustomerService customers;
  private final ProductCatalogService catalog;
  private final InventoryService inventory;
  private final PaymentService payments;
  private final DiscountService discounts;
  private final ShippingEstimateService shippingEstimates;
  private final NotificationService notifications;
  private final JdbcOrderRepository orders;
  private final PoisonProperties poison;

  public OrderPlacementService(
      CustomerService customers,
      ProductCatalogService catalog,
      InventoryService inventory,
      PaymentService payments,
      DiscountService discounts,
      ShippingEstimateService shippingEstimates,
      NotificationService notifications,
      JdbcOrderRepository orders,
      PoisonProperties poison) {
    this.customers = customers;
    this.catalog = catalog;
    this.inventory = inventory;
    this.payments = payments;
    this.discounts = discounts;
    this.shippingEstimates = shippingEstimates;
    this.notifications = notifications;
    this.orders = orders;
    this.poison = poison;
  }

  @Narrated("Placing soak order for customer {customerId}")
  public PlacedOrder placeOrder(
      String customerId,
      List<CartLine> lines,
      String email,
      String cardNumber,
      String sessionCookie,
      String jwt) {
    var customer = customers.findCustomer(customerId);
    log.debug("Resolved customer {} for a {}-line cart", customer.name(), lines.size());
    reserveLines(lines);
    double subtotal = priceLines(customerId, lines);
    var payment = payments.charge(customerId, subtotal, cardNumber);
    var shipping = estimateShipping(lines);
    return persistOrder(customerId, lines, payment.transactionId(), subtotal, shipping.cost());
  }

  private PlacedOrder persistOrder(
      String customerId,
      List<CartLine> lines,
      String transactionId,
      double subtotal,
      double shippingCost) {
    var orderId = orders.nextOrderId();
    var order =
        new PlacedOrder(
            orderId, customerId, transactionId, subtotal, shippingCost, "PLACED", lines);
    orders.save(order);
    notifications.notifyOrderPlaced(customerId, orderId);
    return order;
  }

  private void reserveLines(List<CartLine> lines) {
    for (var line : lines) {
      markPoisonIfMatched(line.quantity());
      inventory.reserve(line.productId(), line.quantity());
    }
  }

  private double priceLines(String customerId, List<CartLine> lines) {
    double subtotal = 0;
    for (var line : lines) {
      double price = catalog.lookupPrice(line.productId());
      var discount = discounts.calculateDiscount(customerId, line.productId());
      subtotal += price * line.quantity() * (1 - discount.percentage());
    }
    return subtotal;
  }

  private ai.narrativetrace.examples.ecommerce.ShippingEstimate estimateShipping(
      List<CartLine> lines) {
    var first = lines.get(0);
    int totalQuantity = lines.stream().mapToInt(CartLine::quantity).sum();
    return shippingEstimates.estimate(first.productId(), totalQuantity);
  }

  /**
   * Marks the request as having reached the domain through the open gate — see README.md "the
   * open-gate design". Cleared per-request by {@code TraceContextFilter}.
   */
  private void markPoisonIfMatched(int quantity) {
    if (quantity == poison.value()) {
      MDC.put("soak.poison", "true");
      log.warn(
          "!! poison {}={} reached the domain unchecked (soak.poison.field={})",
          poison.field(),
          quantity,
          poison.field());
    }
  }

  public PlacedOrder getOrder(String orderId) {
    return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  public PlacedOrder cancelOrder(String orderId) {
    var order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    if ("CANCELLED".equals(order.status())) {
      throw new OrderAlreadyCancelledException(orderId);
    }
    releaseLines(order.lines());
    orders.markCancelled(orderId);
    return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private void releaseLines(List<CartLine> lines) {
    for (var line : lines) {
      inventory.release(line.productId(), line.quantity());
    }
  }
}
