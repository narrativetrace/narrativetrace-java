/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

/** Returns a fixed USPS shipping estimate. */
public class InMemoryShippingEstimateService implements ShippingEstimateService {

  @Override
  public ShippingEstimate estimate(String productId, int quantity) {
    return new ShippingEstimate("USPS", 5.99, 3);
  }
}
