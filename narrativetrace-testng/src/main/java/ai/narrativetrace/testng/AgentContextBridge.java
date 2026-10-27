/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.core.context.NarrativeContext;
import java.lang.reflect.Method;

/**
 * Routes {@code ai.narrativetrace.agent.AgentRuntime}'s shared capture context to this listener's
 * lifecycle-owned one, reflectively — this module carries no dependency on {@code
 * narrativetrace-agent}, so a project without the agent on its test classpath pays nothing and
 * changes nothing.
 *
 * <p>INTENT: {@code AgentRuntime} holds exactly one static {@link NarrativeContext} for the whole
 * JVM. {@link #attach} points it at the lifecycle owner's context once, so method bodies the agent
 * wove at class-load time — no proxy, no annotation — publish into the same pipeline the owner
 * drains, captures and writes per test. {@link #detach} restores whatever context the agent held
 * before attachment.
 *
 * <p>Mirrors the class of the same name in {@code narrativetrace-junit5} and {@code
 * narrativetrace-junit4} — kept as a small duplicate rather than a shared dependency, since none of
 * the three test integrations depends on another.
 */
final class AgentContextBridge {

  private static final String AGENT_RUNTIME_CLASS = "ai.narrativetrace.agent.AgentRuntime";

  /** Shared by every JVM the agent is not attached to — {@code detach()} is then a no-op. */
  private static final AgentContextBridge NONE = new AgentContextBridge(null, null);

  private final NarrativeContext previousContext;
  private final Method setContextMethod;

  private final Method isDeepArmedMethod;
  private final Method beginDeepScopeMethod;
  private final Method endDeepScopeMethod;

  private AgentContextBridge(NarrativeContext previousContext, Method setContextMethod) {
    this(previousContext, setContextMethod, null, null, null);
  }

  private AgentContextBridge(
      NarrativeContext previousContext,
      Method setContextMethod,
      Method isDeepArmedMethod,
      Method beginDeepScopeMethod,
      Method endDeepScopeMethod) {
    this.previousContext = previousContext;
    this.setContextMethod = setContextMethod;
    this.isDeepArmedMethod = isDeepArmedMethod;
    this.beginDeepScopeMethod = beginDeepScopeMethod;
    this.endDeepScopeMethod = endDeepScopeMethod;
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
      return new AgentContextBridge(
          previous,
          setContext,
          optional(runtimeClass, "isDeepArmed"),
          optional(runtimeClass, "beginDeepScope"),
          optional(runtimeClass, "endDeepScope"));
    } catch (ClassNotFoundException absent) {
      return NONE; // the agent module is not on this JVM's classpath — nothing to route
    } catch (Throwable t) { // NOPMD - a bad agent classpath must not fail the test run
      System.err.println(
          "narrative-trace: could not route the agent's context to this listener (" + t + ")");
      return NONE;
    }
  }

  /** An agent older than the deep mode has no such method; that is "not armed", not an error. */
  private static Method optional(Class<?> runtimeClass, String name) {
    try {
      return runtimeClass.getMethod(name);
    } catch (NoSuchMethodException absent) {
      return null;
    }
  }

  /**
   * Whether the agent in this JVM was started with {@code depth=internal} — i.e. whether private
   * methods were woven at all. {@code false} without an agent, and for an agent that predates the
   * deep mode.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  boolean deepArmed() {
    if (isDeepArmedMethod == null || beginDeepScopeMethod == null || endDeepScopeMethod == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(isDeepArmedMethod.invoke(null));
    } catch (Throwable t) { // NOPMD - an optional capability that cannot answer is simply off
      return false;
    }
  }

  /**
   * Opens the agent's deep scope on the calling thread. Only meaningful when {@link #deepArmed}.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  void beginDeepScope() {
    try {
      beginDeepScopeMethod.invoke(null);
    } catch (Throwable t) { // NOPMD - depth is a verbosity aid; it must not fail a test run
      System.err.println("narrative-trace: could not open the agent's deep scope (" + t + ")");
    }
  }

  /**
   * Closes the calling thread's deep scope.
   *
   * @return how many deep spans the agent's cap dropped in it; {@code 0} when none or no scope
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  long endDeepScope() {
    if (endDeepScopeMethod == null) {
      return 0;
    }
    try {
      return ((Number) endDeepScopeMethod.invoke(null)).longValue();
    } catch (Throwable t) { // NOPMD - closing best-effort scope state must not fail the test run
      return 0;
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
