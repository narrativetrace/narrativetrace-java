/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/AcrossServicesRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/follow-one-request-across-services.md: the checkout charges a card
 * through a second service over HTTP, the W3C {@code traceparent} goes with the request, and the
 * callee's own trace carries the caller's trace id and names the caller's span as its parent (rule
 * 8, docs as tests).
 *
 * <p><b>@llmNote</b> The callee's narrative is rendered here, not by the JUnit extension: the
 * extension writes the trace of the context it manages, and the point of this page is that the
 * second service has a context — and a narrative — of its own. The Gradle task {@code
 * recipeAcrossServicesLog} runs this class alone under logback-correlation.xml, so the page's log
 * block holds both services' lines with their trace ids.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AcrossServicesRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private static final Path PAGE_OUTPUT =
      Path.of("build/narrativetrace/recipes/follow-one-request-across-services");

  @Test
  void a_customer_checks_out_against_the_payment_service(NarrativeContext context)
      throws IOException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    try (var paymentService = new PaymentServiceProcess()) {
      var checkout = checkoutCalling(paymentService, context, stock, repository);

      var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

      assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
      assertThat(repository.saved()).hasSize(1);
      writeCalleeNarrative(paymentService);
      assertTheTraceCrossedTheWire(paymentService);
    }
  }

  /** The checkout, with the HTTP payment client where the local one used to be. */
  private static OrderService checkoutCalling(
      PaymentServiceProcess paymentService,
      NarrativeContext context,
      InMemoryInventory stock,
      InMemoryOrderRepository repository) {
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(
            new HttpPaymentService(context, paymentService.endpoint()),
            PaymentService.class,
            context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    return NarrativeTraceProxy.trace(
        new DefaultOrderService(inventory, payments, orders), OrderService.class, context);
  }

  /**
   * One trace, two services: the header named the caller's own span, and the callee's root adopted
   * it as its parent instead of starting a trace of its own.
   */
  private static void assertTheTraceCrossedTheWire(PaymentServiceProcess paymentService) {
    var sent = Traceparent.parse(paymentService.inboundTraceparent());
    assertThat(sent).isNotNull();
    assertThat(sent.traceId()).isEqualTo(Traceparent.parse(DEMO_TRACEPARENT).traceId());

    var calleeRoot = paymentService.lastTrace().roots().get(0);
    assertThat(calleeRoot.signature().methodName()).isEqualTo("charge");
    assertThat(calleeRoot.spanContext().traceId()).isEqualTo(sent.traceId());
    assertThat(calleeRoot.spanContext().parentSpanId()).isEqualTo(sent.parentSpanId());
  }

  /** The callee's own narrative, into the directory the page embeds from. */
  private static void writeCalleeNarrative(PaymentServiceProcess paymentService)
      throws IOException {
    Files.createDirectories(PAGE_OUTPUT);
    Files.writeString(
        PAGE_OUTPUT.resolve("payment-service.md"),
        new MarkdownRenderer().render(paymentService.lastTrace()));
  }
}
