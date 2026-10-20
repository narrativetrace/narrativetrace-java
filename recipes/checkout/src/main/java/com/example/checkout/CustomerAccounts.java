/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/CustomerAccounts.java
package com.example.checkout;

/**
 * Signs a customer in before the checkout charges them. The parameter is named {@code password}.
 */
public interface CustomerAccounts {

  /**
   * @return the session id the checkout carries for the rest of the request
   * @throws IllegalArgumentException when the password is blank
   */
  String signIn(String customerId, String password);
}
