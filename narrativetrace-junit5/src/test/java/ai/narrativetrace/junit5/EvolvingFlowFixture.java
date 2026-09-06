/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Fixture whose traced structure changes between launcher runs: phase one passes with a single call
 * (establishing the last green baseline), phase two adds a nested call and fails — the shape needed
 * to prove the failure surface prints the delta against last green.
 */
@ExtendWith(NarrativeTraceExtension.class)
class EvolvingFlowFixture {

  static volatile boolean grownAndFailing = false;

  @Test
  @DisplayName("trip settles")
  void tripSettles(NarrativeContext context) {
    context.enterMethod(new MethodSignature("Service", "doWork", List.of()));
    if (grownAndFailing) {
      context.enterMethod(new MethodSignature("Ledger", "record", List.of()));
      context.exitMethodWithReturn("recorded");
    }
    context.exitMethodWithReturn("ok");
    assertThat(grownAndFailing).isFalse();
  }
}
