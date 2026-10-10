/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** Two-phase card payments: an authorization holds the money, a confirmation takes it. */
public interface PaymentGateway {

  /** Holds the amount on the customer's card and returns the authorization id. */
  String authorize(String customerId, int amountInCents);

  /** Captures a held authorization: from here on, the customer has paid. */
  void confirm(String authorizationId);
}
