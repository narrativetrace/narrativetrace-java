/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** A test that passes BY throwing — TestNG's {@code expectedExceptions}. */
public class ExpectedExceptionFixture {

  @Test(expectedExceptions = IllegalStateException.class)
  public void anEmptyOrderIsRejected() {
    Traced.fails("OrderService", "placeOrder", new IllegalStateException("empty order"));
    throw new IllegalStateException("empty order");
  }
}
