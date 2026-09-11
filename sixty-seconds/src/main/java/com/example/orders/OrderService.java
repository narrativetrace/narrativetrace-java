/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/orders/OrderService.java
package com.example.orders;

public interface OrderService {
  String placeOrder(String customerId, String productId, int quantity);
}
