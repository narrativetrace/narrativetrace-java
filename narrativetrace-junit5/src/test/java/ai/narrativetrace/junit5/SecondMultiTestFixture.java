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
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class SecondMultiTestFixture {

  @Test
  @DisplayName("customer checks inventory")
  void customerChecksInventory(NarrativeContext context) {
    context.enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    context.exitMethodWithReturn("in-stock");
  }
}
