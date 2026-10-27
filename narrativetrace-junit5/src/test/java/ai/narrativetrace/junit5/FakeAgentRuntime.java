/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

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

  static volatile boolean armed;
  static volatile long dropped;
  static volatile boolean open;
  static final java.util.concurrent.atomic.AtomicInteger begun =
      new java.util.concurrent.atomic.AtomicInteger();
  static final java.util.concurrent.atomic.AtomicInteger ended =
      new java.util.concurrent.atomic.AtomicInteger();

  public static boolean isDeepArmed() {
    return armed;
  }

  public static void beginDeepScope() {
    begun.incrementAndGet();
    open = true;
  }

  public static long endDeepScope() {
    ended.incrementAndGet();
    open = false;
    return dropped;
  }

  /** Resets the shared state between tests, since this stands in for a JVM-static singleton. */
  static void reset() {
    context = NoopNarrativeContext.INSTANCE;
    armed = false;
    open = false;
    dropped = 0;
    begun.set(0);
    ended.set(0);
  }
}
