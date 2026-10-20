/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import ai.narrativetrace.junit5.NarrativeTraceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Run only through {@link GreetingServiceAgentTest}'s nested launch, never discovered directly by
 * Gradle's own {@code test} task (excluded in {@code build.gradle.kts} the same way the other
 * agent-mode fixtures are) — {@link GreetingService} must still be unloaded in this JVM when the
 * agent weaves it, and {@link GreetingServiceAgentTest} is what guarantees that ordering.
 *
 * <p>INTENT: Zero NarrativeTrace code touches {@link GreetingService} here either — only
 * {@code @ExtendWith(NarrativeTraceExtension.class)}, the ordinary way any test opts into per-test
 * files. The §176 proof is that this is now sufficient: the extension routes the agent's own
 * capture into its suite-scoped context, so the files it writes carry {@link
 * GreetingService#greet}'s call with no proxy and no annotation anywhere in this class or {@link
 * GreetingService}.
 */
@ExtendWith(NarrativeTraceExtension.class)
class GreetingServiceAgentFixture {

  @Test
  void greetsByName() {
    var service = new GreetingService();
    var greeting = service.greet("Alice");
    if (!"Hello, Alice!".equals(greeting)) {
      throw new AssertionError("expected 'Hello, Alice!' but got '" + greeting + "'");
    }
  }
}
