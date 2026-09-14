/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

/** A reported (business) outcome — the id is well-formed, the order simply doesn't exist. */
public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(String orderId) {
    super("Order not found: " + orderId);
  }
}
