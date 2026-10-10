/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The checkout path as production wires it, every collaborator traced. */
@ExtendWith(NarrativeTraceExtension.class)
class CheckoutFlowTest {

  @Test
  void customer_checks_out(NarrativeContext context) {
    InvoiceService invoices =
        NarrativeTraceProxy.trace(new DefaultInvoiceService(), InvoiceService.class, context);
    CustomerDirectory customers =
        NarrativeTraceProxy.trace(new KnownCustomers(), CustomerDirectory.class, context);
    ExchangeRates rates = NarrativeTraceProxy.trace(new DailyRates(), ExchangeRates.class, context);
    CurrencyConverter converter =
        NarrativeTraceProxy.trace(new RateTableConverter(rates), CurrencyConverter.class, context);
    PaymentGateway payments =
        NarrativeTraceProxy.trace(new CardPayments(), PaymentGateway.class, context);
    CheckoutService checkout =
        NarrativeTraceProxy.trace(
            Checkout.compose(invoices, customers, converter, payments),
            CheckoutService.class,
            context);

    String invoiceId = checkout.checkout("C-1234", "ORD-7", 4500);

    assertEquals("INV-C-1234-ORD-7-4500", invoiceId);
  }

  /** A card gateway that approves everything. */
  static final class CardPayments implements PaymentGateway {
    @Override
    public String authorize(String customerId, int amount, String currency) {
      return "AUTH-" + customerId;
    }

    @Override
    public void confirm(String authorizationId) {}
  }
}
