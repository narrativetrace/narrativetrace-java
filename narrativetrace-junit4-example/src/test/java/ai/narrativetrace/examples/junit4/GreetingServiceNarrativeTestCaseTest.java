/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.junit4;

import static org.junit.Assert.assertEquals;

import ai.narrativetrace.junit4.NarrativeTestCase;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.Test;

/**
 * Same scenario as {@link GreetingServiceTest}, written against {@link NarrativeTestCase} instead
 * of the two-rule pair directly — the low-ceremony form for suites free to spend their one
 * superclass slot on it. Both forms are kept side by side deliberately: some teams do not want
 * inheritance, and the two-rule form in {@link GreetingServiceTest} stays the primary,
 * always-available way to wire NarrativeTrace into a JUnit 4 suite.
 */
public class GreetingServiceNarrativeTestCaseTest extends NarrativeTestCase {

  @Test
  public void greetsByName() {
    var service =
        NarrativeTraceProxy.trace(new DefaultGreetingService(), GreetingService.class, context());
    assertEquals("greeting message", "Hello, Alice!", service.greet("Alice"));
  }
}
