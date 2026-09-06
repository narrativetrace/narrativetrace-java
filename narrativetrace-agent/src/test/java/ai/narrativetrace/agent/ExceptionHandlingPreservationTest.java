/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves that instrumentation preserves the exception-handling semantics of the original method.
 *
 * <p>Regression tests for the exception-table ordering defect: the injected catch-all handler was
 * registered before the original handlers, so it shadowed user {@code catch}/{@code finally} blocks
 * inside instrumented methods.
 */
class ExceptionHandlingPreservationTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  record TransformedClass(Class<?> clazz, Object instance) {}

  private TransformedClass transformAndLoad(String sampleClass) throws Exception {
    var internalName = "ai/narrativetrace/agent/sample/" + sampleClass;
    var qualifiedName = "ai.narrativetrace.agent.sample." + sampleClass;
    var originalBytes =
        getClass().getClassLoader().getResourceAsStream(internalName + ".class").readAllBytes();
    var transformed = ClassTransformer.transform(originalBytes, internalName);
    var loader = new ByteArrayClassLoader(getClass().getClassLoader(), transformed, qualifiedName);
    var clazz = loader.loadClass(qualifiedName);
    return new TransformedClass(clazz, clazz.getDeclaredConstructor().newInstance());
  }

  @Test
  void userCatchBlockStillHandlesExceptionInsideInstrumentedMethod() throws Exception {
    var tc = transformAndLoad("ResilientService");

    var result = tc.clazz().getMethod("fetchWithFallback").invoke(tc.instance());

    assertThat(result).isEqualTo("fallback");
  }

  @Test
  void internallyHandledExceptionIsRecordedAsNormalReturn() throws Exception {
    var tc = transformAndLoad("ResilientService");

    tc.clazz().getMethod("fetchWithFallback").invoke(tc.instance());

    var root = context.captureTrace().roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("\"fallback\"");
  }

  @Test
  void finallyBlockRunsWhenExceptionEscapesInstrumentedMethod() throws Exception {
    var tc = transformAndLoad("ResilientService");

    assertThatThrownBy(() -> tc.clazz().getMethod("cleanupOnFailure").invoke(tc.instance()))
        .hasCauseInstanceOf(IllegalStateException.class)
        .cause()
        .hasMessage("boom");

    assertThat(tc.clazz().getField("cleanupRan").getBoolean(tc.instance())).isTrue();
  }

  @Test
  void exceptionEscapingPastNonMatchingCatchIsStillRecordedAsThrown() throws Exception {
    var tc = transformAndLoad("ResilientService");

    assertThatThrownBy(() -> tc.clazz().getMethod("rethrowUnhandledType").invoke(tc.instance()))
        .hasCauseInstanceOf(IllegalStateException.class);

    var root = context.captureTrace().roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void monitorIsReleasedWhenExceptionEscapesSynchronizedBlock() throws Exception {
    var tc = transformAndLoad("ResilientService");

    assertThatThrownBy(() -> tc.clazz().getMethod("syncThenThrow").invoke(tc.instance()))
        .hasCauseInstanceOf(IllegalStateException.class);

    var acquired = new CountDownLatch(1);
    var competitor =
        new Thread(
            () -> {
              synchronized (tc.instance()) {
                acquired.countDown();
              }
            });
    competitor.start();
    assertThat(acquired.await(2, TimeUnit.SECONDS))
        .as("monitor must be released after the exception escaped the synchronized block")
        .isTrue();
    competitor.join();
  }
}
