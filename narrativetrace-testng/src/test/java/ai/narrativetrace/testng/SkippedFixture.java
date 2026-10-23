/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** A failing test and one TestNG skips because it depends on it. */
public class SkippedFixture {

  @Test
  public void paymentIsTaken() {
    Traced.call("PaymentService", "charge", "declined");
    throw new AssertionError("payment declined");
  }

  @Test(dependsOnMethods = "paymentIsTaken")
  public void receiptIsSent() {
    Traced.call("ReceiptService", "send", "sent");
  }
}
