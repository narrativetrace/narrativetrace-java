/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/InMemoryInventory.java
package com.example.checkout;

import java.util.HashMap;
import java.util.Map;

/** Stock in a map, so the scenario needs no database. */
public final class InMemoryInventory implements InventoryService {

  private final Map<String, Integer> unitsBySku = new HashMap<>();

  public InMemoryInventory(String sku, int units) {
    unitsBySku.put(sku, units);
  }

  @Override
  public void reserve(String sku, int quantity) {
    var available = availableUnits(sku);
    if (available < quantity) {
      throw new IllegalStateException("not enough stock for " + sku);
    }
    unitsBySku.put(sku, available - quantity);
  }

  @Override
  public void release(String sku, int quantity) {
    unitsBySku.put(sku, availableUnits(sku) + quantity);
  }

  @Override
  public int availableUnits(String sku) {
    return unitsBySku.getOrDefault(sku, 0);
  }
}
