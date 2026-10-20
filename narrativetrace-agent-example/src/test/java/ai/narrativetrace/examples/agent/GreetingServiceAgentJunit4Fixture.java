/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import ai.narrativetrace.junit4.NarrativeTraceClassRule;
import ai.narrativetrace.junit4.NarrativeTraceRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * JUnit 4 counterpart of {@link GreetingServiceAgentFixture}, run only through {@link
 * GreetingServiceAgentJunit4Test}'s nested launch — see that class for why. Public, as every JUnit
 * 4 test class and {@code @Test} method must be.
 */
public class GreetingServiceAgentJunit4Fixture {

  @ClassRule public static final NarrativeTraceClassRule CLASS_RULE = new NarrativeTraceClassRule();

  @Rule public final NarrativeTraceRule rule = CLASS_RULE.testRule();

  @Test
  public void greetsByName() {
    var service = new GreetingService();
    var greeting = service.greet("Alice");
    if (!"Hello, Alice!".equals(greeting)) {
      throw new AssertionError("expected 'Hello, Alice!' but got '" + greeting + "'");
    }
  }
}
