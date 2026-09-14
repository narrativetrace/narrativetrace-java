/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.examples.ecommerce.InventoryService;
import ai.narrativetrace.examples.ecommerce.Reservation;

/**
 * Synchronizes the ecommerce example's {@code InMemoryInventoryService}, whose backing {@code
 * HashMap} is not thread-safe.
 *
 * <p>INTENT: The example is reused unmodified (a soak deliberately runs under concurrent load,
 * which the example's own single-threaded scenarios never exercise) — this decorator is new soak
 * code composed around the reused interface, not a change to the example itself. See README.md
 * "reuse, never copy".
 */
public class ThreadSafeInventoryService implements InventoryService {

  private final InventoryService delegate;
  private final Object lock = new Object();

  public ThreadSafeInventoryService(InventoryService delegate) {
    this.delegate = delegate;
  }

  @Override
  @OnError(
      value = "Insufficient stock for {productId}, requested {quantity}",
      exception = IllegalStateException.class)
  public Reservation reserve(String productId, int quantity) {
    synchronized (lock) {
      return delegate.reserve(productId, quantity);
    }
  }

  @Override
  public void release(String productId, int quantity) {
    synchronized (lock) {
      delegate.release(productId, quantity);
    }
  }
}
