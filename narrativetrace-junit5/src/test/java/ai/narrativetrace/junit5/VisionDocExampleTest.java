/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class VisionDocExampleTest {

  record ReservedInventory(int items, double total) {}

  record Payment(String txId) {}

  record OrderResult(String txId, int items) {}

  interface InventoryService {
    ReservedInventory reserve(String customerId, int quantity);
  }

  interface PaymentService {
    Payment charge(String customerId, double amount);
  }

  interface OrderService {
    OrderResult placeOrder(String customerId, int quantity);
  }

  @Test
  void producesExpectedNarrativeForVisionDocExample(NarrativeContext context) {
    InventoryService inventory =
        NarrativeTraceProxy.trace(
            (InventoryService) (customerId, quantity) -> new ReservedInventory(5, 249.95),
            InventoryService.class,
            context);

    PaymentService payment =
        NarrativeTraceProxy.trace(
            (PaymentService) (customerId, amount) -> new Payment("TXN-8f3a"),
            PaymentService.class,
            context);

    OrderService orders =
        NarrativeTraceProxy.trace(
            (OrderService)
                (customerId, quantity) -> {
                  var reserved = inventory.reserve(customerId, quantity);
                  var pay = payment.charge(customerId, reserved.total());
                  return new OrderResult(pay.txId(), reserved.items());
                },
            OrderService.class,
            context);

    orders.placeOrder("C-123", 5);

    var narrative = new IndentedTextRenderer().render(context.captureTrace());

    assertThat(narrative).contains("OrderService.placeOrder(customerId: \"C-123\", quantity: 5)");
    assertThat(narrative)
        .contains(
            "InventoryService.reserve(customerId: \"C-123\", quantity: 5) → ReservedInventory(items: 5, total: 249.95)");
    assertThat(narrative)
        .contains(
            "PaymentService.charge(customerId: \"C-123\", amount: 249.95) → Payment(txId: \"TXN-8f3a\")");
    assertThat(narrative).contains("→ OrderResult(txId: \"TXN-8f3a\", items: 5)");
    assertThat(narrative).containsPattern("— \\d+ms");
  }
}
