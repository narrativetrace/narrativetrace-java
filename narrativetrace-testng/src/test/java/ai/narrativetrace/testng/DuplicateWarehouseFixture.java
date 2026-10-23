/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** A second class with a scenario of the same name as {@link WarehouseFixture}'s. */
public class DuplicateWarehouseFixture {

  @Test
  public void warehousePacksParcel() {
    Traced.call("WarehouseService", "packParcel", "packed again");
  }
}
