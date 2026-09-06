/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

/** Returns a fixed 10% discount for all known customers. */
public class InMemoryDiscountService implements DiscountService {

  @Override
  public Discount calculateDiscount(String customerId, String productId) {
    return new Discount(customerId, 0.1);
  }
}
