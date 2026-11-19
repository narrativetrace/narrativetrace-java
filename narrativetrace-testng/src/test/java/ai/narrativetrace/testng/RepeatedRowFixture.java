/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Two data-provider rows, each run twice — TestNG's data provider and {@code invocationCount}
 * combined, which is four invocations of one method.
 *
 * <p>Every invocation traces a value no other invocation traces, so an artifact one invocation
 * overwrote is visible as a value that reached no file.
 */
public class RepeatedRowFixture {

  static final AtomicInteger RUN = new AtomicInteger();

  @DataProvider
  public Object[][] equipment() {
    return new Object[][] {{"kayak"}, {"tent"}};
  }

  @Test(dataProvider = "equipment", invocationCount = 2)
  public void equipmentCanBeFound(String item) {
    Traced.call("InventoryService", "find", item + "-" + RUN.incrementAndGet());
  }
}
