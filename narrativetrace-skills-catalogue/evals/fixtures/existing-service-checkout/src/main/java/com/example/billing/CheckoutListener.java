/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/**
 * Called once a checkout's payment has gone through — the place for receipts, emails and analytics,
 * registered in {@link Checkout#compose}. DefaultCheckoutService never talks to a
 * NotificationService itself.
 */
public interface CheckoutListener {

  void onCheckout(String customerId, String invoiceId);
}
