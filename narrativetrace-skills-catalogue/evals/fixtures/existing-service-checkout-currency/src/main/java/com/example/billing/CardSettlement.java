/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** Settles card payments by confirming the authorization with the gateway. */
public class CardSettlement implements PaymentSettlement {

  private final PaymentGateway payments;

  public CardSettlement(PaymentGateway payments) {
    this.payments = payments;
  }

  @Override
  public void settle(String authorizationId) {
    payments.confirm(authorizationId);
  }
}
