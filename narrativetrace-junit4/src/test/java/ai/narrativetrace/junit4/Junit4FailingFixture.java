/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.junit.Assert.fail;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class Junit4FailingFixture {

  @Rule public NarrativeTraceRule narrativeTrace = new NarrativeTraceRule();

  @Test
  public void failingTest() {
    narrativeTrace.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    narrativeTrace.context().exitMethodWithReturn("ok");
    fail("deliberate failure");
  }
}
