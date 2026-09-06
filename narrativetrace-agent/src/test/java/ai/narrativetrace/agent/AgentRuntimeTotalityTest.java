/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.AbstractCollection;
import java.util.Iterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every hook the agent injects into user bytecode is total: it never throws into the instrumented
 * method, not even an {@link Error}.
 *
 * <p>INTENT: {@link HostileValueAgentAttachTest} proves the guarantee end to end with a real {@code
 * -javaagent}; this class pins each hook individually, including the failure modes a subprocess
 * cannot easily stage — a context implementation whose own methods throw.
 */
class AgentRuntimeTotalityTest {

  private NarrativeContext original;

  @BeforeEach
  void setUp() {
    original = AgentRuntime.getContext();
  }

  @AfterEach
  void tearDown() {
    AgentRuntime.setContext(original);
  }

  /** A collection that throws from everything the renderer touches. */
  private static final class ExplodingCollection extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      throw new IllegalStateException("iterator crashed");
    }

    @Override
    public int size() {
      throw new IllegalStateException("size crashed");
    }
  }

  /** A value whose {@code toString()} throws an {@link Error}, not an {@link Exception}. */
  private static final class ErrorOnToString {
    @Override
    public String toString() {
      throw new AssertionError("toString assertion");
    }
  }

  /** A context whose every entry point throws an {@link Error}. */
  private static final class ThrowingContext implements NarrativeContext {
    @Override
    public SpanId enterMethod(MethodSignature signature) {
      throw new AssertionError("enterMethod assertion");
    }

    @Override
    public void detachFrame(SpanId spanId) {
      throw new AssertionError("detachFrame assertion");
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue) {
      throw new AssertionError("exit assertion");
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {
      throw new AssertionError("exit assertion");
    }

    @Override
    public void exitMethodWithReturn(
        String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
      throw new AssertionError("exit assertion");
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext) {
      throw new AssertionError("error exit assertion");
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
      throw new AssertionError("error exit assertion");
    }

    @Override
    public SpanId beginScope(SpanId spanId) {
      throw new AssertionError("beginScope assertion");
    }

    @Override
    public void endScope(SpanId previousScope) {
      throw new AssertionError("endScope assertion");
    }

    @Override
    public TraceTree captureTrace() {
      throw new AssertionError("captureTrace assertion");
    }

    @Override
    public void reset() {
      throw new AssertionError("reset assertion");
    }

    @Override
    public ContextSnapshot snapshot() {
      throw new AssertionError("snapshot assertion");
    }
  }

  @Test
  void aParameterThatThrowsWhileRenderingDoesNotFailTheEnterHook() {
    AgentRuntime.setContext(new ThreadLocalNarrativeContext());

    assertThat(
            AgentRuntime.enterMethod(
                "com.acme.OrderService",
                "handleOrder",
                new String[] {"items"},
                new Object[] {new ExplodingCollection()},
                new boolean[] {false},
                null))
        .isNull();
  }

  @Test
  void aReturnValueThatThrowsWhileRenderingDoesNotFailTheExitHook() {
    AgentRuntime.setContext(new ThreadLocalNarrativeContext());
    AgentRuntime.enterMethod("com.acme.OrderService", "produce");

    assertThatCode(() -> AgentRuntime.exitMethodWithReturn(new ExplodingCollection()))
        .doesNotThrowAnyException();
  }

  @Test
  void aValueWhoseToStringThrowsAnErrorDoesNotFailTheExitHook() {
    AgentRuntime.setContext(new ThreadLocalNarrativeContext());
    AgentRuntime.enterMethod("com.acme.OrderService", "produce");

    assertThatCode(() -> AgentRuntime.exitMethodWithReturn(new ErrorOnToString()))
        .doesNotThrowAnyException();
  }

  @Test
  void aContextThrowingAnErrorFromEnterLeavesTheBareHookSilentAndScopeless() {
    AgentRuntime.setContext(new ThrowingContext());

    assertThat(AgentRuntime.enterMethod("com.acme.OrderService", "handleOrder", "()V", null))
        .isNull();
  }

  @Test
  void aContextThrowingAnErrorFromEnterLeavesTheFullHookSilent() {
    AgentRuntime.setContext(new ThrowingContext());

    assertThat(
            AgentRuntime.enterMethod(
                "com.acme.OrderService",
                "handleOrder",
                new String[] {"id"},
                new Object[] {"A-1"},
                new boolean[] {false},
                null))
        .isNull();
  }

  @Test
  void aContextThrowingAnErrorFromEveryExitPathNeverReachesTheInstrumentedMethod() {
    AgentRuntime.setContext(new ThrowingContext());

    assertThatCode(
            () -> {
              AgentRuntime.exitMethodVoid();
              AgentRuntime.exitMethodWithReturn("value");
              AgentRuntime.exitMethodWithException(new IllegalStateException("business"));
              AgentRuntime.exitMethodWithException(new IllegalStateException("business"), "ctx");
              AgentRuntime.endScope("00000000000000ff");
              AgentRuntime.endScope(null);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void errorContextResolutionThatThrowsYieldsNoContextRatherThanAFailure() {
    AgentRuntime.setContext(new ThreadLocalNarrativeContext());

    assertThat(
            AgentRuntime.resolveErrorContext(
                new IllegalStateException("business"),
                new String[] {"failed for {id}"},
                new String[] {"Ljava/lang/IllegalStateException;"},
                new String[] {"id"},
                null,
                new boolean[] {false}))
        .isNull();
  }
}
