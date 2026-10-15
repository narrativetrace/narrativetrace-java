/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/RefactoredCheckoutFixture.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The test documentation/recipes/review-a-refactoring-did-behaviour-change.md pins: a declined card
 * must save no order. It passes before and after the refactoring — which is the whole point of the
 * page — so only the approved structure can object.
 *
 * <p>A fixture because {@code RefactoringRecipeTest} runs it twice under approval mode, once per
 * version of the checkout, and reads the verdicts.
 */
@ExtendWith(NarrativeTraceExtension.class)
class RefactoredCheckoutFixture {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  /**
   * Which version of the checkout this run wires: the one that releases the reservation when the
   * charge is declined, or the refactoring that dropped that call. A field, because one build has
   * to run both — in your project the refactoring is an edit to one file and this switch does not
   * exist.
   */
  static volatile boolean refactoringDroppedTheRelease;

  @Test
  void a_declined_card_saves_no_order(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));

    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();

    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    var checkout =
        NarrativeTraceProxy.trace(
            refactoringDroppedTheRelease
                ? new OrderServiceWithoutRelease(inventory, payments, orders)
                : new DefaultOrderService(inventory, payments, orders),
            OrderService.class,
            context);

    assertThatThrownBy(
            () -> checkout.placeOrder("C-1234", "SKU-KB", 2, CardPaymentService.DECLINING_CARD))
        .isInstanceOf(PaymentDeclinedException.class);

    assertThat(repository.saved()).isEmpty();
  }
}
