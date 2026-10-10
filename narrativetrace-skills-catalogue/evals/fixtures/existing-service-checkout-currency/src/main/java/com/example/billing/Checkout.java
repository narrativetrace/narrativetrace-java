/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** The composition root: production wires checkout here, and so does CheckoutFlowTest. */
public final class Checkout {

  private Checkout() {}

  public static CheckoutService compose(
      InvoiceService invoices,
      CustomerDirectory customers,
      CurrencyConverter converter,
      PaymentGateway payments) {
    return new DefaultCheckoutService(
        invoices, customers, converter, payments, new CardSettlement(payments));
  }

  /** Production's converter: today's rates from the rate table. */
  public static CurrencyConverter converter() {
    return new RateTableConverter(new DailyRates());
  }
}
