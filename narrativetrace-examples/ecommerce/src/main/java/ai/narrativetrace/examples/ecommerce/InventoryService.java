/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.annotation.OnError;

public interface InventoryService {
  @OnError(
      value = "Insufficient stock for {productId}, requested {quantity}",
      exception = IllegalStateException.class)
  Reservation reserve(String productId, int quantity);

  void release(String productId, int quantity);
}
