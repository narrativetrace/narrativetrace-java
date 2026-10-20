/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;

/**
 * Stand-in for {@code ai.narrativetrace.agent.AgentRuntime}'s {@code getContext}/{@code setContext}
 * shape, resolved by name from {@link AgentContextBridgeTest} through {@link AgentContextBridge}'s
 * test-seam overload — the same pattern {@code PipelineBootstrapTest} uses for the SLF4J narration
 * listener. Public static, as reflection requires.
 */
public final class FakeAgentRuntime {

  private static volatile NarrativeContext context = NoopNarrativeContext.INSTANCE;

  private FakeAgentRuntime() {}

  public static NarrativeContext getContext() {
    return context;
  }

  public static void setContext(NarrativeContext newContext) {
    context = newContext;
  }

  /** Resets the shared state between tests, since this stands in for a JVM-static singleton. */
  static void reset() {
    context = NoopNarrativeContext.INSTANCE;
  }
}
