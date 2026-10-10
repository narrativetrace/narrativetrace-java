/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** Turns an order into an invoice and a payment. */
public interface CheckoutService {

  /** Issues the invoice, takes the payment, and returns the invoice id. */
  String checkout(String customerId, String orderId, int amountInCents);
}
