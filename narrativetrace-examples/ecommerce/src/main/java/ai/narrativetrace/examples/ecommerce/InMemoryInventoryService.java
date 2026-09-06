/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import java.util.HashMap;
import java.util.Map;

public class InMemoryInventoryService implements InventoryService {

  private final Map<String, Integer> stock =
      new HashMap<>(
          Map.of(
              "SKU-MECHANICAL-KB", 150,
              "SKU-MOUSE-PAD", 500,
              "SKU-USB-HUB", 75));

  @Override
  public Reservation reserve(String productId, int quantity) {
    int available = stock.getOrDefault(productId, 0);
    if (available < quantity) {
      throw new IllegalStateException(
          "Insufficient stock for "
              + productId
              + ": requested "
              + quantity
              + ", available "
              + available);
    }
    stock.put(productId, available - quantity);
    return new Reservation(productId, quantity);
  }

  @Override
  public void release(String productId, int quantity) {
    stock.merge(productId, quantity, Integer::sum);
  }
}
