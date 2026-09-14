/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import java.util.List;

/**
 * The persisted/read shape of an order — returned by placement, lookup, and cancellation alike.
 *
 * @param orderId the soak-local order identifier
 * @param customerId the customer the order belongs to
 * @param transactionId the payment confirmation's transaction id, {@code null} once cancelled
 * @param subtotal the charged amount before shipping
 * @param shippingCost the estimated shipping cost, {@code null} for an empty cart
 * @param status {@code PLACED} or {@code CANCELLED}
 * @param lines the cart lines the order was placed with
 */
public record PlacedOrder(
    String orderId,
    String customerId,
    String transactionId,
    double subtotal,
    Double shippingCost,
    String status,
    List<CartLine> lines) {

  /** Returns a copy carrying the given lines — used to attach lines read in a second query. */
  public PlacedOrder withLines(List<CartLine> newLines) {
    return new PlacedOrder(
        orderId, customerId, transactionId, subtotal, shippingCost, status, newLines);
  }
}
