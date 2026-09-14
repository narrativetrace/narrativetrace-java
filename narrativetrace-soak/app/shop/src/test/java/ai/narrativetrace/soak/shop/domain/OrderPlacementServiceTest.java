/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.examples.ecommerce.Customer;
import ai.narrativetrace.examples.ecommerce.CustomerTier;
import ai.narrativetrace.examples.ecommerce.Discount;
import ai.narrativetrace.examples.ecommerce.InMemoryInventoryService;
import ai.narrativetrace.examples.ecommerce.PaymentConfirmation;
import ai.narrativetrace.examples.ecommerce.PaymentDeclinedException;
import ai.narrativetrace.examples.ecommerce.ShippingEstimate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the open-gate design end to end at the domain layer: a poison quantity reaches {@link
 * InMemoryInventoryService#reserve} unmodified and throws, and the poison marker lands in MDC for
 * the exception handler to classify. See {@code web.PlaceOrderRequestValidationTest} for the edge
 * half (the same field passes Bean Validation untouched).
 */
class OrderPlacementServiceTest {

  private static final PoisonProperties POISON = new PoisonProperties("quantity", 1_000);

  private JdbcOrderRepository repository;

  @BeforeEach
  void setUp() {
    var dataSource = new JdbcDataSource();
    dataSource.setUrl("jdbc:h2:mem:order-placement-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    var jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("CREATE SEQUENCE order_id_seq START WITH 1");
    jdbcTemplate.execute(
        "CREATE TABLE orders (order_id VARCHAR(16) PRIMARY KEY, customer_id VARCHAR(64),"
            + " transaction_id VARCHAR(32), subtotal DOUBLE, shipping_cost DOUBLE, status"
            + " VARCHAR(16))");
    jdbcTemplate.execute(
        "CREATE TABLE order_lines (order_id VARCHAR(16), product_id VARCHAR(64), quantity INT)");
    repository = new JdbcOrderRepository(jdbcTemplate);
  }

  @AfterEach
  void clearMdc() {
    MDC.remove("soak.poison");
  }

  private OrderPlacementService service() {
    return new OrderPlacementService(
        customerId -> new Customer(customerId, "Test Customer", CustomerTier.STANDARD),
        productId -> 10.0,
        new ThreadSafeInventoryService(new InMemoryInventoryService()),
        (customerId, amount, cardToken) -> new PaymentConfirmation("TXN-TEST", amount),
        (customerId, productId) -> new Discount(customerId, 0.0),
        (productId, quantity) -> new ShippingEstimate("USPS", 5.0, 3),
        (customerId, orderId) -> CompletableFuture.completedFuture(true),
        repository,
        POISON);
  }

  @Test
  void poisonQuantityReachesTheDomainUncheckedAndThrows() {
    var service = service();
    var lines = List.of(new CartLine("SKU-MECHANICAL-KB", POISON.value()));

    assertThatThrownBy(
            () -> service.placeOrder("C-1234", lines, "a@b.com", "4111", "cookie", "jwt"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insufficient stock");
    assertThat(MDC.get("soak.poison")).isEqualTo("true");
  }

  @Test
  void ordinaryOutOfStockIsNotMarkedAsPoison() {
    var service = service();
    // Above the seeded stock (150) but well below the poison sentinel (1000) — a legitimate
    // business failure, not the open gate.
    var lines = List.of(new CartLine("SKU-MECHANICAL-KB", 151));

    assertThatThrownBy(
            () -> service.placeOrder("C-1234", lines, "a@b.com", "4111", "cookie", "jwt"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(MDC.get("soak.poison")).isNull();
  }

  @Test
  void legitimateOrderIsPersistedAndReadable() {
    var service = service();
    var lines = List.of(new CartLine("SKU-MECHANICAL-KB", 2), new CartLine("SKU-USB-HUB", 1));

    var placed = service.placeOrder("C-1234", lines, "a@b.com", "4111", "cookie", "jwt");

    assertThat(placed.status()).isEqualTo("PLACED");
    assertThat(placed.subtotal()).isEqualTo(30.0);
    var reread = service.getOrder(placed.orderId());
    assertThat(reread.lines()).hasSize(2);
  }

  @Test
  void unknownCustomerIsReportedNotThrownAsPoison() {
    var service = service();
    var lines = List.of(new CartLine("SKU-MECHANICAL-KB", 1));
    var customerOnlyKnowingBroke =
        new OrderPlacementService(
            customerId -> {
              throw new IllegalArgumentException("Customer not found: " + customerId);
            },
            productId -> 10.0,
            new ThreadSafeInventoryService(new InMemoryInventoryService()),
            (customerId, amount, cardToken) -> new PaymentConfirmation("TXN-TEST", amount),
            (customerId, productId) -> new Discount(customerId, 0.0),
            (productId, quantity) -> new ShippingEstimate("USPS", 5.0, 3),
            (customerId, orderId) -> CompletableFuture.completedFuture(true),
            repository,
            POISON);

    assertThatThrownBy(
            () ->
                customerOnlyKnowingBroke.placeOrder(
                    "C-UNKNOWN", lines, "a@b.com", "4111", "cookie", "jwt"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(MDC.get("soak.poison")).isNull();
  }

  @Test
  void paymentDeclinedIsReportedNotThrownAsPoison() {
    var lines = List.of(new CartLine("SKU-MECHANICAL-KB", 1));
    var decliningService =
        new OrderPlacementService(
            customerId -> new Customer(customerId, "Broke", CustomerTier.STANDARD),
            productId -> 10.0,
            new ThreadSafeInventoryService(new InMemoryInventoryService()),
            (customerId, amount, cardToken) -> {
              throw new PaymentDeclinedException("Payment declined for customer " + customerId);
            },
            (customerId, productId) -> new Discount(customerId, 0.0),
            (productId, quantity) -> new ShippingEstimate("USPS", 5.0, 3),
            (customerId, orderId) -> CompletableFuture.completedFuture(true),
            repository,
            POISON);

    assertThatThrownBy(
            () -> decliningService.placeOrder("C-BROKE", lines, "a@b.com", "4111", "cookie", "jwt"))
        .isInstanceOf(PaymentDeclinedException.class);
    assertThat(MDC.get("soak.poison")).isNull();
  }

  @Test
  void cancelReleasesInventoryAndMarksCancelled() {
    var inventory = new ThreadSafeInventoryService(new InMemoryInventoryService());
    var service =
        new OrderPlacementService(
            customerId -> new Customer(customerId, "Test Customer", CustomerTier.STANDARD),
            productId -> 10.0,
            inventory,
            (customerId, amount, cardToken) -> new PaymentConfirmation("TXN-TEST", amount),
            (customerId, productId) -> new Discount(customerId, 0.0),
            (productId, quantity) -> new ShippingEstimate("USPS", 5.0, 3),
            (customerId, orderId) -> CompletableFuture.completedFuture(true),
            repository,
            POISON);
    var placed =
        service.placeOrder(
            "C-1234",
            List.of(new CartLine("SKU-MECHANICAL-KB", 2)),
            "a@b.com",
            "4111",
            "cookie",
            "jwt");

    var cancelled = service.cancelOrder(placed.orderId());

    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    assertThatThrownBy(() -> service.cancelOrder(placed.orderId()))
        .isInstanceOf(OrderAlreadyCancelledException.class);
  }

  @Test
  void unknownOrderIdIsReported() {
    var service = service();

    assertThatThrownBy(() -> service.getOrder("ORD-99999"))
        .isInstanceOf(OrderNotFoundException.class);
  }
}
