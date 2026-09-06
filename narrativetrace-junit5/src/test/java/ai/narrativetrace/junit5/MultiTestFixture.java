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
class MultiTestFixture {

  @Test
  @DisplayName("customer places order")
  void customerPlacesOrder(NarrativeContext context) {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("order-1");
  }

  @Test
  @DisplayName("customer cancels order")
  void customerCancelsOrder(NarrativeContext context) {
    context.enterMethod(new MethodSignature("OrderService", "cancelOrder", List.of()));
    context.exitMethodWithReturn("cancelled");
  }
}
