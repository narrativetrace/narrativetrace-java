/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;

/**
 * An agent runtime shape whose {@code setContext} accepts the bridge's context once and then
 * refuses the restore — the teardown failure {@link AgentContextBridge#detach()} must survive.
 */
public final class RestoreFailingAgentRuntime {

  private static volatile NarrativeContext context = NoopNarrativeContext.INSTANCE;
  private static final java.util.concurrent.atomic.AtomicInteger SETS =
      new java.util.concurrent.atomic.AtomicInteger();

  private RestoreFailingAgentRuntime() {}

  public static NarrativeContext getContext() {
    return context;
  }

  public static void setContext(NarrativeContext newContext) {
    if (SETS.getAndIncrement() > 0) {
      throw new IllegalStateException("restore refused");
    }
    context = newContext;
  }
}
