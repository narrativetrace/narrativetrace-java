/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A data-driven test of three rows, the third failing, beside an ordinary method. */
public class DataProviderFixture {

  @DataProvider
  public Object[][] equipment() {
    return new Object[][] {{"kayak"}, {"tent"}, {"stove"}};
  }

  @Test(dataProvider = "equipment")
  public void equipmentCanBeFound(String item) {
    Traced.call("InventoryService", "find", item);
    if ("stove".equals(item)) {
      throw new AssertionError("no stove in stock");
    }
  }

  @Test
  public void ordinaryLookup() {
    Traced.call("InventoryService", "list", "[]");
  }
}
