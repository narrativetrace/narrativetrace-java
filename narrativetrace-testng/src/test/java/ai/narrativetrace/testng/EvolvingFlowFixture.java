/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.testng.annotations.Test;

/**
 * A test whose structure changes between runs: phase one passes with a single call (the last-green
 * baseline), phase two adds a nested call and fails — so the failure report prints the delta.
 */
public class EvolvingFlowFixture {

  static volatile boolean grownAndFailing;

  @Test
  public void tripSettles() {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature("Service", "doWork", List.of()));
    if (grownAndFailing) {
      Traced.call("Ledger", "record", "recorded");
    }
    context.exitMethodWithReturn("ok");
    if (grownAndFailing) {
      throw new AssertionError("the trip did not settle");
    }
  }
}
