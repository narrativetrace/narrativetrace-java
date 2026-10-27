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

/** An agent whose deep-mode methods exist but fail — depth must never fail a test run. */
public final class BrokenDeepAgentRuntime {

  private BrokenDeepAgentRuntime() {}

  public static NarrativeContext getContext() {
    return NoopNarrativeContext.INSTANCE;
  }

  public static void setContext(NarrativeContext newContext) {}

  public static boolean isDeepArmed() {
    throw new IllegalStateException("cannot answer");
  }

  public static void beginDeepScope() {
    throw new IllegalStateException("cannot begin");
  }

  public static long endDeepScope() {
    throw new IllegalStateException("cannot end");
  }
}
