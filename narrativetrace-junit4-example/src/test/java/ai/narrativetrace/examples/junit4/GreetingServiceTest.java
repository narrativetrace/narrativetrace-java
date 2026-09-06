/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.junit4;

import static org.junit.Assert.assertEquals;

import ai.narrativetrace.junit4.NarrativeTraceClassRule;
import ai.narrativetrace.junit4.NarrativeTraceRule;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class GreetingServiceTest {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void greetsByName() {
    var service =
        NarrativeTraceProxy.trace(
            new DefaultGreetingService(), GreetingService.class, narrativeTrace.context());
    assertEquals("greeting message", "Hello, Alice!", service.greet("Alice"));
  }
}
