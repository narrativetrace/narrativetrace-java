/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.annotation.DeepTrace;
import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.testng.annotations.Test;

/**
 * A public call that leans on a private one — recorded the way the agent records it, so the
 * listener's depth handling is what is under test, not the weaving.
 */
public class DeepFixture {

  private static void priceWithTax() {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature("Pricing", "total", List.of()));
    context.enterMethod(new MethodSignature("Pricing", "withTax", List.of()).asInternal());
    context.exitMethodWithReturn("221");
    context.exitMethodWithReturn("225");
  }

  @Test
  public void plain() {
    priceWithTax();
  }

  @Test
  @DeepTrace
  public void deep() {
    priceWithTax();
  }
}
