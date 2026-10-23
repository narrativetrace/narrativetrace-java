/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/**
 * Two failing tests among three passing ones — the shape of a real triage run — in a fixed order
 * (TestNG priorities), for the pointer, footer and index tests. Named with the FailingTestFixture
 * suffix the build gives every deliberately failing fixture.
 */
public class MixedOutcomeFailingTestFixture {

  @Test(priority = 1)
  public void aPassesFirst() {
    Traced.call("OrderService", "placeOrder", "order-1");
  }

  @Test(priority = 2)
  public void bFailsOnStock() {
    Traced.fails("StockService", "reserve", new IllegalStateException("only 0 left, wanted 3"));
    throw new AssertionError("stock check | failed\nsecond line");
  }

  @Test(priority = 3)
  public void cPassesSecond() {
    Traced.call("OrderService", "cancelOrder", "cancelled");
  }

  @Test(priority = 4)
  public void dFailsOnPayment() {
    Traced.fails("PaymentService", "charge", new IllegalArgumentException("card declined"));
    throw new AssertionError("payment was declined");
  }

  @Test(priority = 5)
  public void ePassesThird() {
    Traced.call("OrderService", "listOrders", "[]");
  }
}
