/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

  private final List<String> events = new ArrayList<>();

  private final PaymentGateway payments =
      new PaymentGateway() {
        @Override
        public String authorize(String customerId, int amountInCents) {
          events.add("authorize " + amountInCents);
          return "AUTH-1";
        }

        @Override
        public void confirm(String authorizationId) {
          events.add("confirm " + authorizationId);
        }
      };

  @Test
  void checkoutIssuesTheInvoiceAndTakesThePayment() {
    CheckoutService checkout =
        new DefaultCheckoutService(
            new DefaultInvoiceService(),
            payments,
            new CardSettlement(payments),
            invoiceId -> {},
            List.of());

    String invoiceId = checkout.checkout("C-1234", "ORD-7", 4500);

    assertEquals("INV-C-1234-ORD-7-4500", invoiceId);
    assertEquals(List.of("authorize 4500", "confirm AUTH-1"), events);
  }
}
