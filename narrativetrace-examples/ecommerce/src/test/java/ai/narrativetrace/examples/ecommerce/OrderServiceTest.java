/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {

  @Test
  void customerPlacesOrderSuccessfully(NarrativeContext context) {
    var orderService = wireServices(context);

    var result = orderService.placeOrder("C-1234", "SKU-MECHANICAL-KB", 2);

    assertThat(result.orderId()).isNotBlank();
    assertThat(result.totalCharged()).isEqualTo(179.98);
    assertThat(result.itemCount()).isEqualTo(2);

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(narrative).contains("OrderService.placeOrder");
    assertThat(narrative).contains("CustomerService.findCustomer");
    assertThat(narrative).contains("ProductCatalogService.lookupPrice");
    assertThat(narrative).contains("InventoryService.reserve");
    assertThat(narrative).contains("PaymentService.charge");
  }

  @Test
  void paymentFailureLeaksInventoryReservation(NarrativeContext context) {
    // C-BROKE is a customer whose payment always gets declined
    var orderService = wireServices(context);

    assertThatThrownBy(() -> orderService.placeOrder("C-BROKE", "SKU-MECHANICAL-KB", 2))
        .isInstanceOf(PaymentDeclinedException.class);

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    // BUG: inventory was reserved but never released after payment failure
    // The trace makes this visible: reserve is called, payment throws,
    // but there is no release call anywhere in the trace
    assertThat(narrative).contains("InventoryService.reserve");
    assertThat(narrative).contains("PaymentService.charge");
    assertThat(narrative).contains("PaymentDeclinedException");
    assertThat(narrative).doesNotContain("InventoryService.release");
  }

  private OrderService wireServices(NarrativeContext context) {
    var customers =
        NarrativeTraceProxy.trace(new InMemoryCustomerService(), CustomerService.class, context);
    var catalog =
        NarrativeTraceProxy.trace(
            new InMemoryProductCatalogService(), ProductCatalogService.class, context);
    var inventory =
        NarrativeTraceProxy.trace(new InMemoryInventoryService(), InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new InMemoryPaymentService(), PaymentService.class, context);

    return NarrativeTraceProxy.trace(
        new DefaultOrderService(customers, catalog, inventory, payments),
        OrderService.class,
        context);
  }
}
