/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

public class DefaultOrderService implements OrderService {

  @Override
  public String placeOrder(String customerId, String productSku, int quantity) {
    return "ORD-" + customerId + "-" + productSku + "-" + quantity;
  }

  @Override
  public boolean isReadyForDispatch(String orderId) {
    return orderId != null && !orderId.isBlank();
  }
}
