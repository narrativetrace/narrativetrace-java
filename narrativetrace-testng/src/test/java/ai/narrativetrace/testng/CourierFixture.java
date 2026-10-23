/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** The second class sharing the JVM's pipeline with {@link WarehouseFixture}. */
public class CourierFixture {

  @Test
  public void courierCollectsParcel() {
    Traced.call("CourierService", "collectParcel", "collected");
  }
}
