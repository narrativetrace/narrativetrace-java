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

/** Two hundred trivial test invocations, each tracing one call — the shared-pipeline proof. */
public class ManyInvocationsFixture {

  static final int COUNT = 200;
  static final AtomicInteger RAN = new AtomicInteger();

  @Test(invocationCount = COUNT)
  public void stockIsCounted() {
    RAN.incrementAndGet();
    Traced.call("StockService", "count", "7");
  }
}
