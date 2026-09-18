/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the render-reentrancy defect: eagerly rendering a parameter or return value that is (or
 * contains) a record whose accessors are themselves woven must never produce spans of its own.
 *
 * <p>Before this guard exists, {@code ValueRenderer} reflectively invoking a woven record accessor
 * during parameter/return capture runs that accessor's own instrumented bytecode, which opens and
 * closes a real span — attributed as a root-level call because no scope is open yet (for parameter
 * rendering) or because the enclosing method's scope was already restored (for return rendering,
 * whose {@code endScope} runs before the return value is rendered). Genuine accessor calls the
 * traced method body itself makes must remain unaffected — the guard is scoped to rendering, not a
 * blanket accessor skip.
 */
class RenderReentrancyGuardTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  @AfterEach
  void tearDown() {
    AgentRuntime.setContext(ai.narrativetrace.core.context.NoopNarrativeContext.INSTANCE);
  }

  /** Everything one test needs from one freshly woven, freshly loaded fixture set. */
  private record Fixtures(
      Object service,
      Class<?> serviceClass,
      Class<?> cartLineClass,
      Class<?> pausableLineClass,
      Class<?> throwingLineClass,
      Object calculator,
      Class<?> calculatorClass) {}

  private static final List<String> WOVEN_SAMPLES =
      List.of("CartLine", "PausableLine", "ThrowingAccessorLine", "LineService", "Calculator");

  /** Transforms and loads a fresh copy of every fixture class this test file needs. */
  private Fixtures loadFixtures() throws Exception {
    var loader = new MultiClassLoader(getClass().getClassLoader());
    for (var simpleName : WOVEN_SAMPLES) {
      var internalName = "ai/narrativetrace/agent/sample/" + simpleName;
      var qualifiedName = "ai.narrativetrace.agent.sample." + simpleName;
      var original =
          getClass().getClassLoader().getResourceAsStream(internalName + ".class").readAllBytes();
      loader.addClass(qualifiedName, ClassTransformer.transform(original, internalName));
    }
    var serviceClass = loader.loadClass("ai.narrativetrace.agent.sample.LineService");
    var calculatorClass = loader.loadClass("ai.narrativetrace.agent.sample.Calculator");
    return new Fixtures(
        serviceClass.getDeclaredConstructor().newInstance(),
        serviceClass,
        loader.loadClass("ai.narrativetrace.agent.sample.CartLine"),
        loader.loadClass("ai.narrativetrace.agent.sample.PausableLine"),
        loader.loadClass("ai.narrativetrace.agent.sample.ThrowingAccessorLine"),
        calculatorClass.getDeclaredConstructor().newInstance(),
        calculatorClass);
  }

  @Test
  void renderingProducesNoSpansForAWovenParameterRecordsAccessors() throws Exception {
    var fx = loadFixtures();
    var line = fx.cartLineClass().getConstructor(String.class, int.class).newInstance("SKU-1", 3);

    fx.serviceClass().getMethod("receive", fx.cartLineClass()).invoke(fx.service(), line);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("receive");
    assertThat(root.children()).isEmpty();
  }

  @Test
  void accessorCallsTheMethodBodyMakesAreStillTracedAsChildren() throws Exception {
    var fx = loadFixtures();
    var line = fx.cartLineClass().getConstructor(String.class, int.class).newInstance("SKU-1", 3);

    var result =
        fx.serviceClass().getMethod("quantityOf", fx.cartLineClass()).invoke(fx.service(), line);
    assertThat(result).isEqualTo(3);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("quantityOf");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("quantity");
  }

  @Test
  void returnValueRenderingProducesNoSpansForAWovenReturnedRecordsAccessors() throws Exception {
    var fx = loadFixtures();

    fx.serviceClass().getMethod("makeLine").invoke(fx.service());

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("makeLine");
    assertThat(root.children()).isEmpty();
  }

  @Test
  void renderingInProgressOnOneThreadDoesNotSuppressAGenuineSpanOnAnotherThread() throws Exception {
    var fx = loadFixtures();
    var enteredRendering = new CountDownLatch(1);
    var releaseRendering = new CountDownLatch(1);
    fx.pausableLineClass().getField("enteredRendering").set(null, enteredRendering);
    fx.pausableLineClass().getField("releaseRendering").set(null, releaseRendering);
    try {
      var threadATrace = new AtomicReference<TraceTree>();
      var receiveThread = startReceivePausableThread(fx, threadATrace);

      assertThat(enteredRendering.await(10, TimeUnit.SECONDS))
          .as("thread A must have entered rendering before thread B's call runs")
          .isTrue();

      var threadBTrace = new AtomicReference<TraceTree>();
      var addResult = new AtomicReference<Object>();
      startCalculatorAddThread(fx, threadBTrace, addResult).join(10_000);

      releaseRendering.countDown();
      receiveThread.join(10_000);

      assertGenuineSpanOnOtherThread(threadBTrace, addResult);
      assertRenderingSuppressedOnFirstThread(threadATrace);
    } finally {
      fx.pausableLineClass().getField("enteredRendering").set(null, null);
      fx.pausableLineClass().getField("releaseRendering").set(null, null);
    }
  }

  /** Starts, and returns, the thread whose parameter rendering pauses on {@code line.sku()}. */
  private Thread startReceivePausableThread(Fixtures fx, AtomicReference<TraceTree> threadATrace)
      throws Exception {
    var line =
        fx.pausableLineClass().getConstructor(String.class, int.class).newInstance("SKU-1", 1);
    var thread =
        new Thread(
            () -> {
              try {
                fx.serviceClass()
                    .getMethod("receivePausable", fx.pausableLineClass())
                    .invoke(fx.service(), line);
                threadATrace.set(context.captureTrace());
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
              }
            });
    thread.start();
    return thread;
  }

  /** Starts, and returns, the thread making a genuine, unrelated woven call on another thread. */
  private Thread startCalculatorAddThread(
      Fixtures fx, AtomicReference<TraceTree> threadBTrace, AtomicReference<Object> addResult) {
    var thread =
        new Thread(
            () -> {
              try {
                addResult.set(
                    fx.calculatorClass()
                        .getMethod("add", int.class, int.class)
                        .invoke(fx.calculator(), 2, 3));
                threadBTrace.set(context.captureTrace());
              } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
              }
            });
    thread.start();
    return thread;
  }

  private void assertGenuineSpanOnOtherThread(
      AtomicReference<TraceTree> threadBTrace, AtomicReference<Object> addResult) {
    assertThat(addResult.get()).isEqualTo(5);
    assertThat(threadBTrace.get().roots()).hasSize(1);
    assertThat(threadBTrace.get().roots().get(0).signature().methodName()).isEqualTo("add");
    assertThat(
            ((TraceOutcome.Returned) threadBTrace.get().roots().get(0).outcome()).renderedValue())
        .isEqualTo("5");
  }

  private void assertRenderingSuppressedOnFirstThread(AtomicReference<TraceTree> threadATrace) {
    assertThat(threadATrace.get().roots()).hasSize(1);
    assertThat(threadATrace.get().roots().get(0).signature().methodName())
        .isEqualTo("receivePausable");
    assertThat(threadATrace.get().roots().get(0).children()).isEmpty();
  }

  @Test
  void aThrowingAccessorDuringRenderingLeavesTheGuardClearedForTheNextGenuineCall()
      throws Exception {
    var fx = loadFixtures();
    var line = fx.throwingLineClass().getConstructor(String.class).newInstance("SKU-1");

    fx.serviceClass()
        .getMethod("receiveThrowing", fx.throwingLineClass())
        .invoke(fx.service(), line);
    var addResult =
        fx.calculatorClass().getMethod("add", int.class, int.class).invoke(fx.calculator(), 2, 3);
    assertThat(addResult).isEqualTo(5);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().stream().map(n -> n.signature().methodName()).toList())
        .containsExactly("receiveThrowing", "add");
    assertThat(tree.roots().get(0).children()).isEmpty();
    assertThat(((TraceOutcome.Returned) tree.roots().get(1).outcome()).renderedValue())
        .isEqualTo("5");
  }
}
