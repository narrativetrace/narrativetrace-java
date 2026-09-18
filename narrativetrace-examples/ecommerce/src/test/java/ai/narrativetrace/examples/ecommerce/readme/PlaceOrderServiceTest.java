/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce.readme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Parity between the README's "before" ({@link PlaceOrderService#placeOrder(OrderRequest)}) and
 * "after" ({@link PlaceOrderService#placeOrderNarrated(OrderRequest)}) methods: same collaborators,
 * same inputs, same result — the only difference release rule 8 permits is the deleted logging.
 */
class PlaceOrderServiceTest {

  private static final Customer CUSTOMER = new Customer("C-1234");
  private static final PaymentConfirmation PAYMENT = new PaymentConfirmation("TXN-1", 89.99);
  private static final Order ORDER = new Order("ORD-1", CUSTOMER.id(), PAYMENT.id());

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(PlaceOrderService.class)).addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    ((Logger) LoggerFactory.getLogger(PlaceOrderService.class)).detachAppender(appender);
  }

  private static PlaceOrderService happyPathService() {
    return new PlaceOrderService(
        id -> CUSTOMER,
        sku -> 89.99,
        (sku, qty) -> {},
        price -> PAYMENT,
        (customer, payment) -> ORDER);
  }

  @Test
  void beforeAndAfterReturnTheSameOrderForTheSameRequest() {
    var request = new OrderRequest("C-1234", "SKU-MECHANICAL-KB", 2);

    var beforeResult = happyPathService().placeOrder(request);
    var afterResult = happyPathService().placeOrderNarrated(request);

    assertThat(beforeResult).isEqualTo(afterResult).isEqualTo(ORDER);
  }

  @Test
  void beforeLogsPlacingAndSucceededLines() {
    happyPathService().placeOrder(new OrderRequest("C-1234", "SKU-MECHANICAL-KB", 2));

    assertThat(appender.list).hasSize(2);
    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("Placing order C-1234");
    assertThat(appender.list.get(1).getFormattedMessage()).isEqualTo("Order succeeded ORD-1");
  }

  @Test
  void afterLogsNothing() {
    happyPathService().placeOrderNarrated(new OrderRequest("C-1234", "SKU-MECHANICAL-KB", 2));

    assertThat(appender.list).isEmpty();
  }

  @Test
  void beforeLogsAndRethrowsOnFailure() {
    var failure = new IllegalStateException("payment declined");
    var service =
        new PlaceOrderService(
            id -> CUSTOMER,
            sku -> 89.99,
            (sku, qty) -> {},
            price -> {
              throw failure;
            },
            (customer, payment) -> ORDER);
    var request = new OrderRequest("C-BROKE", "SKU-MOUSE-PAD", 3);

    assertThatThrownBy(() -> service.placeOrder(request)).isSameAs(failure);

    assertThat(appender.list).hasSize(2);
    assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("Placing order C-BROKE");
    assertThat(appender.list.get(1).getFormattedMessage())
        .isEqualTo("Placing order failed C-BROKE");
    assertThat(appender.list.get(1).getThrowableProxy().getMessage()).isEqualTo("payment declined");
  }
}
