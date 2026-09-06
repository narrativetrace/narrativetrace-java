/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.agent.AgentRuntime;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves agent-mode capture with zero NarrativeTrace code in the test body: {@link GreetingService}
 * is instantiated and called exactly as it would be if this project had never heard of
 * NarrativeTrace. The narration below comes entirely from the {@code -javaagent} the {@code
 * agentTest} task attaches (see {@code build.gradle.kts}); {@link AgentNarrationExtension} only
 * resets and prints what the agent already recorded.
 *
 * <p><b>@llmNote</b> Tagged {@code agent} and excluded from the default {@code test} task on
 * purpose: JaCoCo's own coverage agent and the narrativetrace agent both instrument {@link
 * GreetingService} bytecode when they run in the same JVM, and JaCoCo cannot then match its
 * execution data back to a class file — it zeroes that class's reported coverage rather than
 * failing outright, which would sink the module's coverage gate for a class that {@link
 * GreetingServiceTest} covers perfectly well on its own. {@code agentTest} is wired into {@code
 * check} directly (not coverage-gated) so this proof still runs on every gate, same as {@code
 * test}.
 */
@Tag("agent")
@ExtendWith(AgentNarrationExtension.class)
class GreetingServiceAgentTest {

  @Test
  void greetsByNameWithNoTracingCodeInTheTestBody() {
    var service = new GreetingService();

    var greeting = service.greet("Alice");

    assertThat(greeting).isEqualTo("Hello, Alice!");
    var trace = AgentRuntime.getContext().captureTrace();
    assertThat(new IndentedTextRenderer().render(trace)).contains("GreetingService.greet");
  }
}
