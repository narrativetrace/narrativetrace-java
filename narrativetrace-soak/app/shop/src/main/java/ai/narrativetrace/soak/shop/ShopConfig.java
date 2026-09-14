/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop;

import ai.narrativetrace.agent.AgentRuntime;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.examples.ecommerce.CustomerService;
import ai.narrativetrace.examples.ecommerce.DiscountService;
import ai.narrativetrace.examples.ecommerce.InMemoryCustomerService;
import ai.narrativetrace.examples.ecommerce.InMemoryDiscountService;
import ai.narrativetrace.examples.ecommerce.InMemoryInventoryService;
import ai.narrativetrace.examples.ecommerce.InMemoryPaymentService;
import ai.narrativetrace.examples.ecommerce.InMemoryShippingEstimateService;
import ai.narrativetrace.examples.ecommerce.InventoryService;
import ai.narrativetrace.examples.ecommerce.NotificationService;
import ai.narrativetrace.examples.ecommerce.PaymentService;
import ai.narrativetrace.examples.ecommerce.ShippingEstimateService;
import ai.narrativetrace.soak.shop.domain.HttpNotificationService;
import ai.narrativetrace.soak.shop.domain.JdbcOrderRepository;
import ai.narrativetrace.soak.shop.domain.JdbcProductCatalogService;
import ai.narrativetrace.soak.shop.domain.OrderPlacementService;
import ai.narrativetrace.soak.shop.domain.PoisonProperties;
import ai.narrativetrace.soak.shop.domain.ThreadSafeInventoryService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the ecommerce example's reused domain services and this module's new JDBC/HTTP
 * implementations into one {@link OrderPlacementService}.
 */
@Configuration
public class ShopConfig {

  @Bean
  public NarrativeContext narrativeContext() {
    return AgentRuntime.getContext();
  }

  @Bean
  public CustomerService customerService() {
    return new InMemoryCustomerService();
  }

  /**
   * The example's seeded stock (150/500/75 — sized for a handful of demo scenarios, see {@code
   * InMemoryInventoryService}) is exhausted within the first seconds of sustained load: a 10-minute
   * smoke run at 20 req/s with 15% of traffic placing multi-line orders places roughly 1,600
   * orders, each averaging ~10 units per SKU — nearly all of {@code POST /orders} started failing
   * as (correctly, but unintentionally) out-of-stock once real stock ran out, found running the P1
   * smoke test (k6's "order placed" check: 16 passes, 1593 fails). Restocked here via the reused
   * {@code release(productId, quantity)} — composition, no change to the example — to a level that
   * comfortably survives a two-hour soak's cumulative demand while staying an order of magnitude
   * below the deliberate out-of-stock probe's quantity (see k6/scenario.js's
   * businessFailureRequest) and three orders of magnitude below the poison sentinel.
   */
  private static final int RESTOCK_QUANTITY = 1_000_000;

  private static final String[] CATALOG_PRODUCT_IDS = {
    "SKU-MECHANICAL-KB", "SKU-MOUSE-PAD", "SKU-USB-HUB"
  };

  @Bean
  public InventoryService inventoryService() {
    var inventory = new ThreadSafeInventoryService(new InMemoryInventoryService());
    for (var productId : CATALOG_PRODUCT_IDS) {
      inventory.release(productId, RESTOCK_QUANTITY);
    }
    return inventory;
  }

  @Bean
  public PaymentService paymentService() {
    return new InMemoryPaymentService();
  }

  @Bean
  public DiscountService discountService() {
    return new InMemoryDiscountService();
  }

  @Bean
  public ShippingEstimateService shippingEstimateService() {
    return new InMemoryShippingEstimateService();
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService notificationExecutor() {
    return Executors.newCachedThreadPool();
  }

  @Bean
  public NotificationService notificationService(
      NarrativeContext narrativeContext,
      Executor notificationExecutor,
      @Value("${soak.notify.url:http://localhost:8081}") String notifyBaseUrl) {
    return new HttpNotificationService(narrativeContext, notifyBaseUrl, notificationExecutor);
  }

  @Bean
  public OrderPlacementService orderPlacementService(
      CustomerService customerService,
      JdbcProductCatalogService catalogService,
      InventoryService inventoryService,
      PaymentService paymentService,
      DiscountService discountService,
      ShippingEstimateService shippingEstimateService,
      NotificationService notificationService,
      JdbcOrderRepository orderRepository,
      PoisonProperties poisonProperties) {
    return new OrderPlacementService(
        customerService,
        catalogService,
        inventoryService,
        paymentService,
        discountService,
        shippingEstimateService,
        notificationService,
        orderRepository,
        poisonProperties);
  }
}
