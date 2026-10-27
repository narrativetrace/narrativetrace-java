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

/** An agent that predates the deep mode: the context accessors and nothing else. */
public final class LegacyAgentRuntime {

  private static volatile NarrativeContext context = NoopNarrativeContext.INSTANCE;

  private LegacyAgentRuntime() {}

  public static NarrativeContext getContext() {
    return context;
  }

  public static void setContext(NarrativeContext newContext) {
    context = newContext;
  }
}
