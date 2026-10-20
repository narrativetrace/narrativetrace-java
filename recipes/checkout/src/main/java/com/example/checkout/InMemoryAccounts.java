/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/InMemoryAccounts.java
package com.example.checkout;

/** Accounts in memory, so the scenario needs no identity provider. */
public final class InMemoryAccounts implements CustomerAccounts {

  @Override
  public String signIn(String customerId, String password) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password must not be blank");
    }
    return "SESSION-" + customerId;
  }
}
