/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.util.List;

public class DefaultCheckoutService implements CheckoutService {

  private final InvoiceService invoices;
  private final PaymentGateway payments;
  private final PaymentSettlement settlement;
  private final LedgerService ledger;
  private final List<CheckoutListener> listeners;

  public DefaultCheckoutService(
      InvoiceService invoices,
      PaymentGateway payments,
      PaymentSettlement settlement,
      LedgerService ledger,
      List<CheckoutListener> listeners) {
    this.invoices = invoices;
    this.payments = payments;
    this.settlement = settlement;
    this.ledger = ledger;
    this.listeners = List.copyOf(listeners);
  }

  @Override
  public String checkout(String customerId, String orderId, int amountInCents) {
    String invoiceId = invoices.issueInvoice(customerId, orderId, amountInCents);
    String authorization = payments.authorize(customerId, amountInCents);
    for (CheckoutListener listener : listeners) {
      listener.onCheckout(customerId, invoiceId);
    }
    settlement.settle(authorization);
    return invoiceId;
  }
}
