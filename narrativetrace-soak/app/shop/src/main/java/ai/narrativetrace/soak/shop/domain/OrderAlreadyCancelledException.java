/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

/** A reported (business) outcome — cancelling an order twice is a conflict, not a poison path. */
public class OrderAlreadyCancelledException extends RuntimeException {

  public OrderAlreadyCancelledException(String orderId) {
    super("Order already cancelled: " + orderId);
  }
}
