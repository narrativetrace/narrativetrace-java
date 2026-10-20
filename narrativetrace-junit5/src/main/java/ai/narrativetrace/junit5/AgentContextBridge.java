/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.core.context.NarrativeContext;
import java.lang.reflect.Method;

/**
 * Routes {@code ai.narrativetrace.agent.AgentRuntime}'s shared capture context to this extension's
 * lifecycle-owned one, reflectively — this module carries no dependency on {@code
 * narrativetrace-agent}, so a project without the agent on its test classpath pays nothing and
 * changes nothing.
 *
 * <p>INTENT: {@code AgentRuntime} holds exactly one static {@link NarrativeContext} for the whole
 * JVM. {@link #attach} points it at the extension's suite-scoped context once, so method bodies the
 * agent wove at class-load time — no proxy, no annotation — publish into the same pipeline the
 * extension drains, captures and writes per test. {@link #detach} restores whatever context the
 * agent held before attachment, so a JVM that later detaches the extension (or never had it) is
 * left exactly as it was found.
 *
 * <p><b>@llmNote</b> {@code AgentRuntime.getContext}/{@code setContext} take and return the real
 * {@link NarrativeContext} type — only the {@code AgentRuntime} class itself is resolved by name,
 * the same shape {@code PipelineBootstrap.narrationListener} uses for the SLF4J listener. A class
 * not found is the ordinary case (no agent attached) and stays silent; anything else reaching this
 * class loading or invoking it is a misconfiguration worth one line on stderr, never a failure of
 * the test run that asked for tracing.
 */
final class AgentContextBridge {

  private static final String AGENT_RUNTIME_CLASS = "ai.narrativetrace.agent.AgentRuntime";

  /** Shared by every JVM the agent is not attached to — {@code detach()} is then a no-op. */
  private static final AgentContextBridge NONE = new AgentContextBridge(null, null);

  private final NarrativeContext previousContext;
  private final Method setContextMethod;

  private AgentContextBridge(NarrativeContext previousContext, Method setContextMethod) {
    this.previousContext = previousContext;
    this.setContextMethod = setContextMethod;
  }

  /**
   * Points the agent's shared context at {@code context}, when the agent is attached to this JVM.
   *
   * @param context the lifecycle owner's context the agent should publish into
   * @return a bridge whose {@link #detach()} restores the agent's previous context; a no-op
   *     instance when the agent class cannot be resolved
   */
  static AgentContextBridge attach(NarrativeContext context) {
    return attach(context, AGENT_RUNTIME_CLASS);
  }

  /**
   * Test seam: which class is resolved — see {@code PipelineBootstrap}'s own {@code
   * narrationListenerClass} constructor parameter for the same pattern. Package-private, because
   * which class the agent routes into is a fact about the module layout, not a deployment choice.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // an optional, reflectively-resolved dependency
  static AgentContextBridge attach(NarrativeContext context, String runtimeClassName) {
    try {
      var runtimeClass = Class.forName(runtimeClassName);
      var getContext = runtimeClass.getMethod("getContext");
      var setContext = runtimeClass.getMethod("setContext", NarrativeContext.class);
      var previous = (NarrativeContext) getContext.invoke(null);
      setContext.invoke(null, context);
      return new AgentContextBridge(previous, setContext);
    } catch (ClassNotFoundException absent) {
      return NONE; // the agent module is not on this JVM's classpath — nothing to route
    } catch (Throwable t) { // NOPMD - a bad agent classpath must not fail the test run
      System.err.println(
          "narrative-trace: could not route the agent's context to this extension (" + t + ")");
      return NONE;
    }
  }

  /** Restores the context the agent held before {@link #attach}. A no-op for {@link #NONE}. */
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  void detach() {
    if (setContextMethod == null) {
      return;
    }
    try {
      setContextMethod.invoke(null, previousContext);
    } catch (Throwable t) { // NOPMD - restoring on best-effort teardown must not fail the test run
      System.err.println(
          "narrative-trace: could not restore the agent's previous context (" + t + ")");
    }
  }
}
