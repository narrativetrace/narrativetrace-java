/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

/** One method TestNG runs three times — TestNG's repeated test. */
public class InvocationCountFixture {

  static final AtomicInteger RUN = new AtomicInteger();

  @Test(invocationCount = 3)
  public void ticketIsIssued() {
    Traced.call("TicketService", "issue", "ticket-" + RUN.incrementAndGet());
  }
}
