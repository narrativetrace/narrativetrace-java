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
 * A test that always passes but whose traced structure a flag can change — the shape an approval
 * rejection needs: the run's only failure is the approval verdict.
 */
public class ApprovalDriftFixture {

  static volatile boolean extraQuery;

  @Test
  public void bookingIsStored() {
    Traced.call("BookingService", "store", "stored");
    if (extraQuery) {
      Traced.call("InventoryService", "availableUnits", "2");
    }
  }
}
