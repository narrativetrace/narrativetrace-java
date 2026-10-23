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

/** A traced call whose narration template names a parameter that does not exist. */
public class TemplateFixture {

  @Test
  public void placesOrderWithATypo() {
    var context = NarrativeTraceListener.context();
    context.enterMethod(
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), "Placing order for {custmerId}", null));
    context.exitMethodWithReturn("ok");
  }
}
