/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/PaymentDeclinedException.java
package com.example.checkout;

/** The gateway refused the card. Says nothing about the stock that is still reserved. */
public class PaymentDeclinedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PaymentDeclinedException(String message) {
    super(message);
  }
}
