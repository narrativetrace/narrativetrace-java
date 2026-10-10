/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import java.util.List;

/** The composition root: production wires checkout here, and so does CheckoutFlowTest. */
public final class Checkout {

  private Checkout() {}

  public static CheckoutService compose(
      InvoiceService invoices,
      PaymentGateway payments,
      LedgerService ledger,
      NotificationService notifications) {
    List<CheckoutListener> listeners = List.of();
    return new DefaultCheckoutService(
        invoices, payments, new CardSettlement(payments), ledger, listeners);
  }
}
