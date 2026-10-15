/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/InventoryService.java
package com.example.checkout;

/** The stock the checkout reserves before it charges, and releases when it must not. */
public interface InventoryService {

  void reserve(String sku, int quantity);

  void release(String sku, int quantity);

  int availableUnits(String sku);
}
