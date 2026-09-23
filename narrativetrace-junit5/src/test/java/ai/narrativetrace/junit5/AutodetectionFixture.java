/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deliberately carries no {@code @ExtendWith(NarrativeTraceExtension.class)} — {@link
 * ExtensionAutodetectionTest} proves that {@code NarrativeContext} resolution and the aggregate
 * Clarity report still happen when the launcher enables JUnit's own ServiceLoader-based extension
 * autodetection instead of a per-class annotation.
 */
class AutodetectionFixture {

  @Test
  @DisplayName("warehouse restocks a shelf")
  void warehouseRestocksShelf(NarrativeContext context) {
    context.enterMethod(new MethodSignature("InventoryService", "restockShelf", List.of()));
    context.exitMethodWithReturn("restocked");
  }
}
