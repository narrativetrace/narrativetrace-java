/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

/** What the business knows about a customer's card. */
public interface CustomerDirectory {

  /** The ISO 4217 code of the currency the customer's card is billed in. */
  String cardCurrency(String customerId);
}
