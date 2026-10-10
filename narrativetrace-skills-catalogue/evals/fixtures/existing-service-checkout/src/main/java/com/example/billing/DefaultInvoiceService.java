/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** The invoice numbering every checkout starts from. */
public class DefaultInvoiceService implements InvoiceService {

  @Override
  public String issueInvoice(String customerId, String orderId, int amountInCents) {
    if (amountInCents <= 0) {
      throw new IllegalArgumentException("an invoice needs a positive amount");
    }
    return "INV-" + customerId + "-" + orderId + "-" + amountInCents;
  }

  @Override
  public boolean isSettled(String invoiceId) {
    return invoiceId.endsWith("-0");
  }
}
