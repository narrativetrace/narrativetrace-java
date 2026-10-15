/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/InMemoryOrderRepository.java
package com.example.checkout;

import java.util.ArrayList;
import java.util.List;

/** Saved orders in a list, so the scenario needs no database. */
public final class InMemoryOrderRepository implements OrderRepository {

  private final List<Order> saved = new ArrayList<>();

  @Override
  public void save(Order order) {
    saved.add(order);
  }

  /** Every order saved so far, in the order they were saved — what a test asserts on. */
  public List<Order> saved() {
    return List.copyOf(saved);
  }
}
