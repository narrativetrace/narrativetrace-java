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
 * Deliberately the wrong shape — no {@code setContext} method — so {@link AgentContextBridgeTest}
 * can exercise {@link AgentContextBridge#attach}'s {@code catch (Throwable)} branch: a
 * misconfigured classpath must degrade to the no-op bridge, not fail the test run.
 */
public final class MalformedAgentRuntime {

  private MalformedAgentRuntime() {}

  public static NarrativeContext getContext() {
    return NoopNarrativeContext.INSTANCE;
  }
}
