/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Two same-shape calls whose argument differs — the run loop folding summarizes, and the shape the
 * evaluation read as {@code ×1 more: #2} with the second SKU nowhere on the page.
 */
@ExtendWith(NarrativeTraceExtension.class)
class LoopFixture {

  @Test
  void catalogIsReadTwice(NarrativeContext context) {
    lookUp(context, "KAYAK");
    lookUp(context, "TENT");
  }

  private static void lookUp(NarrativeContext context, String sku) {
    context.enterMethod(
        new MethodSignature(
            "CatalogService",
            "findEquipment",
            List.of(new ParameterCapture("sku", "\"" + sku + "\"", false))));
    context.exitMethodWithReturn("\"" + sku + "\"");
  }
}
