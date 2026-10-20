/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentContextBridgeTest {

  @AfterEach
  void resetFakeRuntime() {
    FakeAgentRuntime.reset();
  }

  @Test
  void attachingWhenTheAgentClassIsAbsentReturnsANoOpBridge() {
    var bridge = AgentContextBridge.attach(new ThreadLocalNarrativeContext(), "does.not.Exist");

    bridge.detach(); // must not throw — nothing to restore
  }

  @Test
  void attachingWhenTheAgentIsPresentRoutesItsContextAndDetachRestoresIt() {
    var previous = NoopNarrativeContext.INSTANCE;
    FakeAgentRuntime.setContext(previous);
    var owned = new ThreadLocalNarrativeContext();

    var bridge = AgentContextBridge.attach(owned, "ai.narrativetrace.junit5.FakeAgentRuntime");

    assertThat(FakeAgentRuntime.getContext()).isSameAs(owned);

    bridge.detach();

    assertThat(FakeAgentRuntime.getContext()).isSameAs(previous);
  }

  @Test
  void attachingAMisshapenClassDegradesToANoOpBridgeAndWarnsOnStderr() {
    var oldErr = System.err;
    var captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured));
    NarrativeContext owned = new ThreadLocalNarrativeContext();
    try {
      var bridge =
          AgentContextBridge.attach(owned, "ai.narrativetrace.junit5.MalformedAgentRuntime");

      bridge.detach(); // no-op: never reaches the missing setContext method
    } finally {
      System.setErr(oldErr);
    }

    assertThat(captured.toString()).contains("could not route the agent's context");
  }
}
