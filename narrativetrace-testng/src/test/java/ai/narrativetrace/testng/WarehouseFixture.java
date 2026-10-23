/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** One of two classes sharing the JVM's pipeline; each file must hold only its own test's call. */
public class WarehouseFixture {

  @Test
  public void warehousePacksParcel() {
    Traced.call("WarehouseService", "packParcel", "packed");
  }

  @Test
  public void warehouseShipsParcel() {
    Traced.call("WarehouseService", "shipParcel", "shipped");
  }
}
