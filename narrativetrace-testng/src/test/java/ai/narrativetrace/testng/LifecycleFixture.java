/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** A test whose {@code @BeforeMethod} and {@code @AfterMethod} trace calls of their own. */
public class LifecycleFixture {

  @BeforeMethod
  public void prepare() {
    Traced.call("FixtureService", "prepare", "ready");
  }

  @Test
  public void orderIsPlaced() {
    Traced.call("OrderService", "placeOrder", "order-1");
  }

  @AfterMethod
  public void clean() {
    Traced.call("FixtureService", "clean", "clean");
  }
}
