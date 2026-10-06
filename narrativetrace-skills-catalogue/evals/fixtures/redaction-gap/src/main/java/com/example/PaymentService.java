/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

/**
 * Wrapped with {@code NarrativeTraceProxy.trace(...)} elsewhere in the real project this fixture
 * stands in for — {@code authToken} is a recognized sensitive parameter name. Deliberately no test
 * anywhere in this fixture asserts the literal {@code "[REDACTED]"}: the gap the deviation case
 * exists to catch.
 */
public class PaymentService {

  public String charge(String authToken, String amount) {
    return "charged " + amount;
  }
}
