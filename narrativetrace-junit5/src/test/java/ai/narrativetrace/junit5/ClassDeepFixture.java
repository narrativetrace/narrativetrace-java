/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.annotation.DeepTrace;
import ai.narrativetrace.core.context.NarrativeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Depth requested once, on the class. */
@DeepTrace
@ExtendWith(NarrativeTraceExtension.class)
class ClassDeepFixture {

  @Test
  void anyTest(NarrativeContext context) {
    context.enterMethod(
        new ai.narrativetrace.api.event.MethodSignature("A", "b", java.util.List.of()));
    context.exitMethodWithReturn("1");
  }
}
