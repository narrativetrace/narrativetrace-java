/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What an instrumented method does when tracing is off: nothing at all.
 *
 * <p>INTENT: The injected call site asks {@link AgentRuntime#isActive()} before it marshals
 * anything, so an inactive context is never called and the arguments are never boxed into an {@code
 * Object[]}. This class pins the observable half of that — no call reaches the context — because a
 * call that never happens is the only proof that the arrays feeding it were never built.
 */
class InactiveCallSiteTest {

  private NarrativeContext original;

  @BeforeEach
  void setUp() {
    original = AgentRuntime.getContext();
  }

  @AfterEach
  void tearDown() {
    AgentRuntime.setContext(original);
  }

  /** A context that answers {@link #isActive()} and records every other call it receives. */
  private static final class CountingContext implements NarrativeContext {

    private final boolean active;
    private final List<String> calls = new ArrayList<>();

    CountingContext(boolean active) {
      this.active = active;
    }

    @Override
    public boolean isActive() {
      return active;
    }

    @Override
    public SpanId enterMethod(MethodSignature signature) {
      calls.add("enterMethod");
      return SpanId.of("00000000000000ff");
    }

    @Override
    public void detachFrame(SpanId spanId) {
      calls.add("detachFrame");
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue) {
      calls.add("exitMethodWithReturn");
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {
      calls.add("exitMethodWithReturn");
    }

    @Override
    public void exitMethodWithReturn(
        String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
      calls.add("exitMethodWithReturn");
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext) {
      calls.add("exitMethodWithException");
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
      calls.add("exitMethodWithException");
    }

    @Override
    public SpanId beginScope(SpanId spanId) {
      calls.add("beginScope");
      return null;
    }

    @Override
    public void endScope(SpanId previousScope) {
      calls.add("endScope");
    }

    @Override
    public TraceTree captureTrace() {
      calls.add("captureTrace");
      return null;
    }

    @Override
    public void reset() {
      calls.add("reset");
    }

    @Override
    public ContextSnapshot snapshot() {
      calls.add("snapshot");
      return null;
    }
  }

  private Object invokeInstrumentedAdd() throws Exception {
    var internalName = "ai/narrativetrace/agent/sample/Calculator";
    var qualifiedName = "ai.narrativetrace.agent.sample.Calculator";
    var originalBytes =
        getClass().getClassLoader().getResourceAsStream(internalName + ".class").readAllBytes();
    var transformed = ClassTransformer.transform(originalBytes, internalName);
    var loader = new ByteArrayClassLoader(getClass().getClassLoader(), transformed, qualifiedName);
    var clazz = loader.loadClass(qualifiedName);
    var instance = clazz.getDeclaredConstructor().newInstance();
    return clazz.getMethod("add", int.class, int.class).invoke(instance, 2, 3);
  }

  @Test
  void anInactiveContextIsNeverCalledByAnInstrumentedMethod() throws Exception {
    var context = new CountingContext(false);
    AgentRuntime.setContext(context);

    assertThat(invokeInstrumentedAdd()).isEqualTo(5);
    assertThat(context.calls).isEmpty();
  }

  private static List<String> callSiteShape(String methodName) throws Exception {
    var internalName = "ai/narrativetrace/agent/sample/Calculator";
    var originalBytes =
        InactiveCallSiteTest.class
            .getClassLoader()
            .getResourceAsStream(internalName + ".class")
            .readAllBytes();
    return CallSiteShape.of(ClassTransformer.transform(originalBytes, internalName), methodName);
  }

  @Test
  void theGateIsReadBeforeAnyArgumentIsMarshalled() throws Exception {
    var shape = callSiteShape("add");

    assertThat(shape).startsWith("isActive", "branch");
    assertThat(shape.indexOf("array")).isGreaterThan(shape.indexOf("branch"));
  }

  @Test
  void anActiveContextStillSeesEnterScopeAndExit() throws Exception {
    var context = new CountingContext(true);
    AgentRuntime.setContext(context);

    assertThat(invokeInstrumentedAdd()).isEqualTo(5);
    assertThat(context.calls)
        .containsExactly("enterMethod", "beginScope", "endScope", "exitMethodWithReturn");
  }
}
