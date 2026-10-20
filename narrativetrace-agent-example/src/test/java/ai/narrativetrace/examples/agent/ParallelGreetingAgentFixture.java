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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Two tests run concurrently (see {@code junit.jupiter.execution.parallel.*} configuration
 * parameters {@link ParallelAgentIsolationTest} passes to the nested launch) against the one
 * suite-owned context {@link NarrativeTraceExtension} routes the agent into — §176 build step 3:
 * the context's state is thread-local, so two tests sharing one object on two threads at once must
 * still each see only their own call, with no thread-routed context needed in the agent.
 */
@ExtendWith(NarrativeTraceExtension.class)
class ParallelGreetingAgentFixture {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  void greetsAlice() {
    new GreetingService().greet("Alice-only");
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  void greetsBob() {
    new GreetingService().greet("Bob-only");
  }
}
