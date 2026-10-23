/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** Two traced tests and one that traces nothing — the plainest TestNG class there is. */
public class OrderFixture {

  @Test
  public void customerPlacesOrder() {
    Traced.call("OrderService", "placeOrder", "order-1");
  }

  @Test
  public void customerCancelsOrder() {
    Traced.call("OrderService", "cancelOrder", "cancelled");
  }

  @Test
  public void quietTest() {
    // traces nothing, so it writes nothing
  }
}
