/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.annotation.DeepTrace;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A public call that leans on a private one — recorded the way the agent records it, so the
 * extension's depth handling is what is under test, not the weaving.
 */
@ExtendWith(NarrativeTraceExtension.class)
class DeepFixture {

  private static void priceWithTax(NarrativeContext context) {
    context.enterMethod(new MethodSignature("Pricing", "total", List.of()));
    context.enterMethod(new MethodSignature("Pricing", "withTax", List.of()).asInternal());
    context.exitMethodWithReturn("221");
    context.exitMethodWithReturn("225");
  }

  @Test
  @DisplayName("prices are plain")
  void plain(NarrativeContext context) {
    priceWithTax(context);
  }

  @Test
  @DeepTrace
  @DisplayName("prices are deep")
  void deep(NarrativeContext context) {
    priceWithTax(context);
  }
}
