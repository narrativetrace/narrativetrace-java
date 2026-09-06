/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class MarkdownDocumentTest {

  @Test
  void happyPathProducesFullMarkdownDocument(NarrativeContext context) {
    var orderService = wireServices(context);

    orderService.placeOrder("C-1234", "SKU-MECHANICAL-KB", 2);

    var tree = context.captureTrace();
    var metadata = new TraceMetadata("Customer places order successfully", ScenarioResult.SUCCESS);
    var markdown = new MarkdownRenderer().renderDocument(tree, metadata);

    assertFrontmatter(markdown);
    assertThat(markdown).contains("## Trace: OrderService.placeOrder");
    assertThat(markdown).contains("**Scenario:** Customer places order successfully");
    assertThat(markdown).contains("**Result:** PASSED");
    assertThat(markdown).contains("### Call Flow");
    assertCallFlow(markdown);
  }

  private void assertFrontmatter(String markdown) {
    assertThat(markdown).startsWith("---\n");
    assertThat(markdown).contains("type: trace");
    assertThat(markdown).contains("scenario: Customer places order successfully");
    assertThat(markdown).contains("entry_point: OrderService.placeOrder");
    assertThat(markdown).contains("method_count: 5");
    assertThat(markdown).contains("error_count: 0");
  }

  private void assertCallFlow(String markdown) {
    assertThat(markdown).contains("- **OrderService.placeOrder**");
    assertThat(markdown).contains("  - **CustomerService.findCustomer**");
    assertThat(markdown).contains("  - **ProductCatalogService.lookupPrice**");
    assertThat(markdown).contains("  - **InventoryService.reserve**");
    assertThat(markdown).contains("  - **PaymentService.charge**");
    assertThat(markdown).contains("→ `OrderResult(");
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
