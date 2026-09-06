/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(NarrativeTraceExtension.class)
class EndToEndNarrativeTest {

  interface InventoryService {
    boolean checkStock(String itemId);
  }

  interface OrderService {
    String placeOrder(String customerId);
  }

  @Test
  void proxyContextRendererAndExtensionProduceReadableNarrative(NarrativeContext context) {
    InventoryService inventory =
        NarrativeTraceProxy.trace(
            (InventoryService) itemId -> true, InventoryService.class, context);
    OrderService orders =
        NarrativeTraceProxy.trace(
            (OrderService)
                customerId -> {
                  inventory.checkStock("ITEM-1");
                  return "order-42";
                },
            OrderService.class,
            context);

    orders.placeOrder("C-123");

    var narrative = new IndentedTextRenderer().render(context.captureTrace());

    assertThat(narrative).contains("OrderService.placeOrder(customerId: \"C-123\")");
    assertThat(narrative).contains("InventoryService.checkStock(itemId: \"ITEM-1\") → true");
    assertThat(narrative).contains("→ \"order-42\"");
    assertThat(narrative).containsPattern("— \\d+ms");
  }

  record ReservedInventory(int items, double total) {}

  record Payment(String txId) {}

  record OrderResult(String txId, int items) {}

  interface InventoryServiceFull {
    ReservedInventory reserve(String customerId, int quantity);
  }

  interface PaymentService {
    Payment charge(String customerId, double amount);
  }

  interface OrderServiceFull {
    OrderResult placeOrder(String customerId, int quantity);
  }

  @Test
  void producesFullMarkdownTraceWithHumanizedScenario(
      NarrativeContext context, @TempDir Path tempDir) throws Exception {
    var orders = wireOrderServices(context);
    orders.placeOrder("C-123", 5);

    var out = new ByteArrayOutputStream();
    NarrativeRenderer mermaid = new MermaidSequenceDiagramRenderer()::render;
    NarrativeRenderer plantuml = new PlantUmlSequenceDiagramRenderer()::render;
    TraceTestSupport.writeTraceFile(
        "com.example.OrderServiceTest",
        "customerPlacesOrderWithLoyaltyDiscount",
        "customerPlacesOrderWithLoyaltyDiscount()",
        context.captureTrace(),
        false,
        tempDir,
        new PrintStream(out),
        "markdown",
        mermaid,
        plantuml);

    var content =
        Files.readString(
            tempDir.resolve(
                "traces/OrderServiceTest/customer_places_order_with_loyalty_discount.md"));
    assertFrontmatter(content);
    assertThat(content).contains("**Scenario:** Customer places order with loyalty discount");
    assertThat(content).contains("**Result:** PASSED");
    assertCallFlow(content);
  }

  private OrderServiceFull wireOrderServices(NarrativeContext context) {
    InventoryServiceFull inventory =
        NarrativeTraceProxy.trace(
            (InventoryServiceFull) (customerId, quantity) -> new ReservedInventory(5, 249.95),
            InventoryServiceFull.class,
            context);
    PaymentService payment =
        NarrativeTraceProxy.trace(
            (PaymentService) (customerId, amount) -> new Payment("TXN-8f3a"),
            PaymentService.class,
            context);
    return NarrativeTraceProxy.trace(
        (OrderServiceFull)
            (customerId, quantity) -> {
              var reserved = inventory.reserve(customerId, quantity);
              var pay = payment.charge(customerId, reserved.total());
              return new OrderResult(pay.txId(), reserved.items());
            },
        OrderServiceFull.class,
        context);
  }

  private void assertFrontmatter(String content) {
    assertThat(content).startsWith("---\n");
    assertThat(content).contains("type: trace");
    assertThat(content).contains("scenario: Customer places order with loyalty discount");
  }

  private void assertCallFlow(String content) {
    assertThat(content).contains("**OrderServiceFull.placeOrder**");
    assertThat(content).contains("customerId: `\"C-123\"`");
    assertThat(content).contains("quantity: `5`");
    assertThat(content).contains("**InventoryServiceFull.reserve**");
    assertThat(content).contains("**PaymentService.charge**");
    assertThat(content).contains("amount: `249.95`");
    assertThat(content).contains("OrderResult(txId: \"TXN-8f3a\", items: 5)");
  }
}
