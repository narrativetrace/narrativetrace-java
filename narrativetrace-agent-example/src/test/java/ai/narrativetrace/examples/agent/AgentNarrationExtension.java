/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import ai.narrativetrace.agent.AgentRuntime;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for lifecycle and output only — it performs no tracing itself.
 *
 * <p>INTENT: Agent-mode capture already happened by the time any extension callback runs — the
 * {@code -javaagent} wove {@link GreetingService} at class-load time, before the test JVM's {@code
 * main()} even started. This extension's only job is resetting {@link AgentRuntime}'s shared
 * context between tests and printing what it captured: the same lifecycle/output role {@code
 * NarrativeTraceRule} and {@code NarrativeTraceExtension} play for the proxy and JUnit 5 paths,
 * just without also owning the capture.
 *
 * <p><b>@llmNote</b> {@link AgentRuntime#getContext()} is one static field for the whole test JVM
 * (there is exactly one java agent attached), so {@link #beforeEach} resets it — otherwise a second
 * test would see the first test's call still in its trace.
 */
public final class AgentNarrationExtension implements BeforeEachCallback, AfterEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    AgentRuntime.getContext().reset();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    var trace = AgentRuntime.getContext().captureTrace();
    if (trace.isEmpty()) {
      return;
    }
    System.out.println(new IndentedTextRenderer().render(trace));
  }
}
