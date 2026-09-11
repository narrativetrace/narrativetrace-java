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

/**
 * A test that always <em>passes</em> but whose traced structure a flag can change — the shape an
 * approval rejection needs.
 *
 * <p>The evaluation's own reproduction: add one extra query after the assertions, watch approval
 * reject it, then remove the line again. Nothing here asserts, because the point is a run whose
 * only failure is the approval verdict.
 */
@ExtendWith(NarrativeTraceExtension.class)
class ApprovalDriftFixture {

  static volatile boolean extraQuery;

  @Test
  @DisplayName("booking is stored")
  void bookingIsStored(NarrativeContext context) {
    context.enterMethod(new MethodSignature("BookingService", "store", List.of()));
    context.exitMethodWithReturn("stored");
    if (extraQuery) {
      context.enterMethod(new MethodSignature("InventoryService", "availableUnits", List.of()));
      context.exitMethodWithReturn("2");
    }
  }
}
