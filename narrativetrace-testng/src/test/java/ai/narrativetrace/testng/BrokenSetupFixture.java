/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** A {@code @BeforeMethod} that traces a call and then fails, so TestNG skips the test. */
public class BrokenSetupFixture {

  @BeforeMethod
  public void connect() {
    Traced.call("ConnectionPool", "open", "refused");
    throw new IllegalStateException("database unreachable");
  }

  @Test
  public void orderIsStored() {
    Traced.call("OrderRepository", "store", "stored");
  }
}
