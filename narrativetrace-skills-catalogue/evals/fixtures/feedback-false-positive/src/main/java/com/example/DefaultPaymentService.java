/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

/** The implementation the test wraps with {@code NarrativeTraceProxy.trace(...)}. */
public class DefaultPaymentService implements PaymentService {

  @Override
  public String charge(String customerId, String authToken, String amount) {
    return "PAY-" + customerId;
  }
}
