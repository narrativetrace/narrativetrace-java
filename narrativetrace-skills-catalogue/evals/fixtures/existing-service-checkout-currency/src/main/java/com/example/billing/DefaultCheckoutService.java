/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

public class DefaultCheckoutService implements CheckoutService {

  private final InvoiceService invoices;
  private final CustomerDirectory customers;
  private final CurrencyConverter converter;
  private final PaymentGateway payments;
  private final PaymentSettlement settlement;

  public DefaultCheckoutService(
      InvoiceService invoices,
      CustomerDirectory customers,
      CurrencyConverter converter,
      PaymentGateway payments,
      PaymentSettlement settlement) {
    this.invoices = invoices;
    this.customers = customers;
    this.converter = converter;
    this.payments = payments;
    this.settlement = settlement;
  }

  @Override
  public String checkout(String customerId, String orderId, int amountInCents) {
    String invoiceId = invoices.issueInvoice(customerId, orderId, amountInCents);
    String currency = customers.cardCurrency(customerId);
    int charged = converter.convert(amountInCents, currency);
    String authorization = payments.authorize(customerId, charged, currency);
    settlement.settle(authorization);
    return invoiceId;
  }
}
