/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain unit test for {@link GreetingService}'s own behavior, independent of tracing — the {@code
 * -javaagent} that makes {@link GreetingServiceAgentTest} interesting only attaches to the separate
 * {@code agentTest} task (see {@code build.gradle.kts}), never to this module's default {@code
 * test} task. Two javaagents instrumenting the same class file at once (JaCoCo's coverage agent and
 * the narrativetrace agent) leave JaCoCo unable to match execution data back to a class file, which
 * zeroes that class's reported coverage — so the coverage gate is satisfied here, where JaCoCo runs
 * alone.
 */
class GreetingServiceTest {

  @Test
  void greetsByName() {
    var service = new GreetingService();

    assertThat(service.greet("Alice")).isEqualTo("Hello, Alice!");
  }
}
