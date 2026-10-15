/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/CardPaymentService.java
package com.example.checkout;

/** A card gateway stub: it declines the one test card gateways document as declining. */
public final class CardPaymentService implements PaymentService {

  /** The card every gateway's test suite declines. Luhn-valid, like a real one. */
  public static final String DECLINING_CARD = "4000000000000002";

  @Override
  public String charge(String customerId, int amountCents, String card) {
    if (DECLINING_CARD.equals(card)) {
      throw new PaymentDeclinedException("card declined by the issuer");
    }
    return "PAY-" + customerId + "-" + amountCents;
  }
}
