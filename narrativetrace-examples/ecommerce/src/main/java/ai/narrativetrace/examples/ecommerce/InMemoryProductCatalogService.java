/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import java.util.Map;

public class InMemoryProductCatalogService implements ProductCatalogService {

  private final Map<String, Double> prices =
      Map.of(
          "SKU-MECHANICAL-KB", 89.99,
          "SKU-MOUSE-PAD", 24.99,
          "SKU-USB-HUB", 39.99);

  @Override
  public double lookupPrice(String productId) {
    var price = prices.get(productId);
    if (price == null) {
      throw new IllegalArgumentException("Product not found: " + productId);
    }
    return price;
  }
}
