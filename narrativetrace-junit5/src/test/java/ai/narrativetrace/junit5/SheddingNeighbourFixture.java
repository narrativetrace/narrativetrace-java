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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Two ordered tests sharing one suite-owned context: the first publishes far more events than a
 * small ring holds, the second makes one call — read back by {@link TraceLossPerTestTest}.
 */
@ExtendWith(NarrativeTraceExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SheddingNeighbourFixture {

  @Test
  @Order(1)
  void shedsUnderLoad(NarrativeContext context) {
    for (int i = 0; i < 40; i++) {
      context.enterMethod(new MethodSignature("BulkImporter", "importRow", List.of()));
      context.exitMethodWithReturn("\"ok\"");
    }
  }

  @Test
  @Order(2)
  void makesOneCall(NarrativeContext context) {
    context.enterMethod(new MethodSignature("Pricing", "total", List.of()));
    context.exitMethodWithReturn("200");
  }
}
