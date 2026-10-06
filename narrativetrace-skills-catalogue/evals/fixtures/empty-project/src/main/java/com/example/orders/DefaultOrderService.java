/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

/** A plain implementation — nothing here has ever touched NarrativeTrace. */
public class DefaultOrderService implements OrderService {

  @Override
  public String placeOrder(String customerId, String productId, int quantity) {
    return "ORD-" + customerId + "-" + productId + "-" + quantity;
  }
}
