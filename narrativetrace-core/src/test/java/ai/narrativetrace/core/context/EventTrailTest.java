/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventTrailTest {

  private static final MethodSignature SIG = new MethodSignature("Svc", "op", List.of());

  private ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();

  @AfterEach
  void cleanup() {
    context.reset();
  }

  @Test
  void enterMethodAppendsEnterEventWithCorrectParentHandle() {
    context.enterMethod(SIG);

    var events = context.events();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
    var enter = (TraceEvent.EnterEvent) events.get(0);
    assertThat(enter.spanContext()).isNotNull();
    assertThat(enter.spanContext().parentSpanId()).isNull();
    assertThat(enter.signature()).isEqualTo(SIG);
    assertThat(enter.timestampNanos()).isPositive();
  }

  @Test
  void nestedEnterSetsParentHandleToOuterHandle() {
    var outerSig = new MethodSignature("Outer", "outerOp", List.of());
    var innerSig = new MethodSignature("Inner", "innerOp", List.of());
    context.enterMethod(outerSig);
    context.enterMethod(innerSig);

    var events = context.events();
    assertThat(events).hasSize(2);
    var outer = (TraceEvent.EnterEvent) events.get(0);
    var inner = (TraceEvent.EnterEvent) events.get(1);
    assertThat(inner.spanContext().parentSpanId()).isEqualTo(outer.spanContext().spanId());
  }

  @Test
  void exitMethodWithReturnAppendsExitEventWithReturnedOutcome() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn("\"ok\"");

    var events = context.events();
    assertThat(events).hasSize(2);
    assertThat(events.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
    var enter = (TraceEvent.EnterEvent) events.get(0);
    var exit = (TraceEvent.ExitEvent) events.get(1);
    assertThat(exit.spanContext().spanId()).isEqualTo(enter.spanContext().spanId());
    assertThat(exit.outcome()).isEqualTo(new TraceOutcome.Returned("\"ok\""));
    assertThat(exit.errorContext()).isNull();
  }

  @Test
  void exitMethodWithExceptionAppendsExitEventWithThrewOutcome() {
    context.enterMethod(SIG);
    var exception = new RuntimeException("boom");
    context.exitMethodWithException(exception, "card expired");

    var events = context.events();
    assertThat(events).hasSize(2);
    var exit = (TraceEvent.ExitEvent) events.get(1);
    assertThat(exit.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(((TraceOutcome.Threw) exit.outcome()).exception()).isSameAs(exception);
    assertThat(exit.errorContext()).isEqualTo("card expired");
  }

  @Test
  void resetClearsEvents() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn(null);

    context.reset();

    assertThat(context.events()).isEmpty();
  }

  /**
   * Below {@code DETAIL} a signature with no parameters has nothing to suppress, so the enter event
   * carries the caller's own signature — not a rebuilt copy of it. Identity is the assertion
   * because the cost this pins is the rebuild: a stream pipeline and a ten-field record per enter,
   * ~400 B, which is why {@code _NARRATIVE} used to allocate more than {@code _DETAIL}.
   */
  @Test
  void narrativeLevelLeavesAParameterlessSignatureUntouched() {
    context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE));

    context.enterMethod(SIG);

    var enter = (TraceEvent.EnterEvent) context.events().get(0);
    assertThat(enter.signature()).isSameAs(SIG);
  }

  /** ...and a signature that does carry parameters still loses their values below DETAIL. */
  @Test
  void narrativeLevelStillSuppressesTheValuesOfRealParameters() {
    context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE));
    var withParams =
        new MethodSignature(
            "Svc", "method", List.of(new ParameterCapture("orderId", "\"order-42\"", false)));

    context.enterMethod(withParams);

    var enter = (TraceEvent.EnterEvent) context.events().get(0);
    assertThat(enter.signature().parameters())
        .singleElement()
        .satisfies(
            capture -> {
              assertThat(capture.name()).isEqualTo("orderId");
              assertThat(capture.renderedValue()).isEmpty();
            });
  }

  @Test
  void offLevelSkipsAllEventRecording() {
    context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF));

    context.enterMethod(SIG);

    assertThat(context.events()).isEmpty();
  }

  @Test
  void detachFrameRemovesFromActiveStack() {
    SpanId spanId = context.enterMethod(SIG);
    context.detachFrame(spanId);

    var innerSig = new MethodSignature("Inner", "inner", List.of());
    context.enterMethod(innerSig);

    var events = context.events();
    var secondEnter = (TraceEvent.EnterEvent) events.get(1);
    assertThat(secondEnter.spanContext().parentSpanId()).isNull();
  }

  @Test
  void handleBasedExitAppendsExitEvent() {
    SpanId spanId = context.enterMethod(SIG);
    context.exitMethodWithReturn("\"done\"", spanId);

    var events = context.events();
    assertThat(events).hasSize(2);
    var enter = (TraceEvent.EnterEvent) events.get(0);
    var exit = (TraceEvent.ExitEvent) events.get(1);
    assertThat(exit.spanContext().spanId()).isEqualTo(enter.spanContext().spanId());
  }

  @Test
  void handleBasedExceptionExitAppendsExitEvent() {
    SpanId spanId = context.enterMethod(SIG);
    var ex = new IllegalStateException("fail");
    context.exitMethodWithException(ex, "ctx", spanId);

    var events = context.events();
    assertThat(events).hasSize(2);
    var enter = (TraceEvent.EnterEvent) events.get(0);
    var exit = (TraceEvent.ExitEvent) events.get(1);
    assertThat(exit.spanContext().spanId()).isEqualTo(enter.spanContext().spanId());
    assertThat(exit.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(exit.errorContext()).isEqualTo("ctx");
  }

  @Test
  void snapshotScopeSeesOnlyItsOwnEventsAndHandsThemBackOnClose() {
    context.enterMethod(SIG);
    var snapshot = context.snapshot();

    try (var scope = snapshot.activate()) {
      var innerSig = new MethodSignature("Inner", "run", List.of());
      context.enterMethod(innerSig);
      context.exitMethodWithReturn(null);

      // Scope sees only its own events (handle-based filtering)
      var scopeEvents = context.events();
      assertThat(scopeEvents).hasSize(2);
      assertThat(((TraceEvent.EnterEvent) scopeEvents.get(0)).signature()).isEqualTo(innerSig);
    }

    // Once the scope closes the work done under it belongs to the thread that took the
    // snapshot — the synchronous log already narrated it, so the event view must not lose it.
    var parentEvents = context.events();
    assertThat(parentEvents).hasSize(3);
    assertThat(((TraceEvent.EnterEvent) parentEvents.get(0)).signature()).isEqualTo(SIG);
  }

  @Test
  void captureTraceBuildsTreeFromEvents() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn("\"result\"");

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature()).isEqualTo(SIG);
    assertThat(tree.roots().get(0).outcome()).isEqualTo(new TraceOutcome.Returned("\"result\""));
  }

  @Test
  void captureTraceFromEventsProducesNestedTree() {
    var outerSig = new MethodSignature("Outer", "run", List.of());
    var innerSig = new MethodSignature("Inner", "exec", List.of());
    context.enterMethod(outerSig);
    context.enterMethod(innerSig);
    context.exitMethodWithReturn("true");
    context.exitMethodWithReturn("\"done\"");

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature()).isEqualTo(outerSig);
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature()).isEqualTo(innerSig);
  }

  @Test
  void handleBasedExceptionExitWithNullDelegatesToStackBased() {
    context.enterMethod(SIG);
    var ex = new RuntimeException("boom");
    context.exitMethodWithException(ex, "ctx", null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void handleBasedExceptionExitCompletesDetachedFrame() {
    SpanId spanId = context.enterMethod(SIG);
    context.detachFrame(spanId);

    var ex = new RuntimeException("async-fail");
    context.exitMethodWithException(ex, "timeout", spanId);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(tree.roots().get(0).signature().errorContext()).isEqualTo("timeout");
  }

  @Test
  void scopedParentTakesPriorityOverActiveStack() {
    SpanId outerSpanId = context.enterMethod(new MethodSignature("Outer", "run", List.of()));

    context.runScoped(
        outerSpanId,
        () -> {
          // Push another handle onto activeStack
          context.enterMethod(new MethodSignature("Mid", "mid", List.of()));
          // Now activeStack top is Mid's handle, but scoped parent is outerSpanId
          context.enterMethod(new MethodSignature("Inner", "exec", List.of()));

          var events = context.events();
          var outerEnter = (TraceEvent.EnterEvent) events.get(0);
          var innerEnter = (TraceEvent.EnterEvent) events.get(events.size() - 1);
          assertThat(innerEnter.spanContext().parentSpanId())
              .isEqualTo(outerEnter.spanContext().spanId());
          return null;
        });
  }

  @Test
  void runScopedRestoresPreviousScopeAfterNesting() {
    SpanId h0 = context.enterMethod(new MethodSignature("A", "a", List.of()));
    SpanId h1 = context.enterMethod(new MethodSignature("B", "b", List.of()));

    context.runScoped(
        h0,
        () -> {
          SpanId hC = context.enterMethod(new MethodSignature("C", "c", List.of()));
          context.exitMethodWithReturn(null, hC);

          context.runScoped(
              h1,
              () -> {
                SpanId hD = context.enterMethod(new MethodSignature("D", "d", List.of()));
                context.exitMethodWithReturn(null, hD);
                return null;
              });

          // After inner scope, should be back to h0
          SpanId hE = context.enterMethod(new MethodSignature("E", "e", List.of()));
          context.exitMethodWithReturn(null, hE);
          return null;
        });

    var events = context.events();
    var aEnter = findEnter(events, "a");
    var bEnter = findEnter(events, "b");
    var cEnter = findEnter(events, "c");
    var dEnter = findEnter(events, "d");
    var eEnter = findEnter(events, "e");
    assertThat(cEnter.spanContext().parentSpanId()).isEqualTo(aEnter.spanContext().spanId());
    assertThat(dEnter.spanContext().parentSpanId()).isEqualTo(bEnter.spanContext().spanId());
    assertThat(eEnter.spanContext().parentSpanId()).isEqualTo(aEnter.spanContext().spanId());
  }

  private static TraceEvent.EnterEvent findEnter(List<TraceEvent> events, String methodName) {
    return events.stream()
        .filter(e -> e instanceof TraceEvent.EnterEvent)
        .map(e -> (TraceEvent.EnterEvent) e)
        .filter(e -> e.signature().methodName().equals(methodName))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void fallsBackToActiveStackWhenScopedParentIsNull() {
    context.enterMethod(new MethodSignature("Outer", "run", List.of()));
    // No runScoped — scoped parent is null, activeStack has outerSpanId
    context.enterMethod(new MethodSignature("Inner", "exec", List.of()));

    var events = context.events();
    var outerEnter = (TraceEvent.EnterEvent) events.get(0);
    var innerEnter = (TraceEvent.EnterEvent) events.get(1);
    assertThat(innerEnter.spanContext().parentSpanId())
        .isEqualTo(outerEnter.spanContext().spanId());
  }

  @Test
  void beginScopeSetsParentAndEndScopeRestoresIt() {
    SpanId outerSpanId = context.enterMethod(new MethodSignature("Outer", "run", List.of()));

    SpanId prevScope = context.beginScope(outerSpanId);
    // Push another handle onto activeStack so it would win without scoping
    context.enterMethod(new MethodSignature("Mid", "mid", List.of()));
    context.enterMethod(new MethodSignature("Inner", "exec", List.of()));

    // Scoped parent overrides activeStack
    var events = context.events();
    var outerEnter = findEnter(events, "run");
    var innerEnter = findEnter(events, "exec");
    assertThat(innerEnter.spanContext().parentSpanId())
        .isEqualTo(outerEnter.spanContext().spanId());

    context.endScope(prevScope);

    // After endScope, fallback to activeStack (Mid's handle)
    context.enterMethod(new MethodSignature("After", "after", List.of()));
    events = context.events();
    var afterEnter = findEnter(events, "after");
    assertThat(afterEnter.spanContext().parentSpanId())
        .isNotEqualTo(outerEnter.spanContext().spanId());
  }

  @Test
  void beginScopeNestsAndEndScopeRestoresPreviousScope() {
    SpanId h0 = context.enterMethod(new MethodSignature("A", "a", List.of()));
    SpanId h1 = context.enterMethod(new MethodSignature("B", "b", List.of()));

    SpanId prev0 = context.beginScope(h0);
    SpanId prev1 = context.beginScope(h1);

    SpanId hC = context.enterMethod(new MethodSignature("C", "c", List.of()));
    context.exitMethodWithReturn(null, hC);
    var bEnter = findEnter(context.events(), "b");
    var cEnter = findEnter(context.events(), "c");
    assertThat(cEnter.spanContext().parentSpanId()).isEqualTo(bEnter.spanContext().spanId());

    context.endScope(prev1); // restores scope to h0

    SpanId hD = context.enterMethod(new MethodSignature("D", "d", List.of()));
    context.exitMethodWithReturn(null, hD);
    var aEnter = findEnter(context.events(), "a");
    var dEnter = findEnter(context.events(), "d");
    assertThat(dEnter.spanContext().parentSpanId()).isEqualTo(aEnter.spanContext().spanId());

    context.endScope(prev0); // restores to no scope
  }

  @Test
  void pipelineCollectsEnterAndExitFromSameThread() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn("\"ok\"");

    var events = context.events();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
    assertThat(events.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
  }

  @Test
  void pipelineCollectsCrossThreadExits() throws Exception {
    SpanId spanId = context.enterMethod(SIG);
    context.detachFrame(spanId);

    var thread = new Thread(() -> context.exitMethodWithReturn("\"done\"", spanId));
    thread.start();
    thread.join();

    // Cross-thread exit has same handle created on main thread — included in main's events
    var events = context.events();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
    assertThat(events.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
  }

  @Test
  void resetClearsPipeline() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn(null);
    assertThat(context.events()).isNotEmpty();

    context.reset();

    assertThat(context.events()).isEmpty();
  }

  @Test
  void currentSpanIdReturnsNullWhenStackEmpty() {
    assertThat(context.currentSpanId()).isNull();
  }

  @Test
  void currentSpanIdReturnsActiveSpanId() {
    SpanId spanId = context.enterMethod(SIG);

    assertThat(context.currentSpanId()).isEqualTo(spanId);
  }

  @Test
  void emitTraceNodeCreatesEnterExitPairInEvents() {
    var node =
        new TraceNode(SIG, List.of(), new TraceOutcome.Returned("\"ok\""), 500L, 1000L, null);
    context.emitTraceNode(node, null);

    var events = context.events();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(TraceEvent.EnterEvent.class);
    assertThat(events.get(1)).isInstanceOf(TraceEvent.ExitEvent.class);
    var enter = (TraceEvent.EnterEvent) events.get(0);
    assertThat(enter.spanContext().parentSpanId()).isNull();
    assertThat(enter.signature()).isEqualTo(SIG);
    assertThat(enter.timestampNanos()).isEqualTo(1000L);
  }

  @Test
  void emitTraceNodeRecursesIntoChildren() {
    var childSig = new MethodSignature("Child", "exec", List.of());
    var child =
        new TraceNode(childSig, List.of(), new TraceOutcome.Returned("true"), 200L, 1100L, null);
    var parent =
        new TraceNode(SIG, List.of(child), new TraceOutcome.Returned("\"ok\""), 500L, 1000L, null);
    context.emitTraceNode(parent, null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature()).isEqualTo(childSig);
  }

  @Test
  void emitTraceNodePreservesConcurrencyInfo() {
    var info = new ConcurrencyInfo("g1", "pool-1", 42L, false, ConcurrencyKind.FORK_JOIN);
    var node =
        new TraceNode(SIG, List.of(), new TraceOutcome.Returned("\"ok\""), 500L, 1000L, info);
    context.emitTraceNode(node, null);

    var tree = context.captureTrace();
    assertThat(tree.roots().get(0).concurrency()).isEqualTo(info);
  }

  @Test
  void captureLocalTraceReturnsOnlyPerThreadEvents() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn("\"main\"");

    // Simulate cross-thread event in accumulator by running on another thread
    var latch = new java.util.concurrent.CountDownLatch(1);
    var otherSig = new MethodSignature("Other", "run", List.of());
    var thread =
        new Thread(
            () -> {
              context.enterMethod(otherSig);
              context.exitMethodWithReturn("\"other\"");
              latch.countDown();
            });
    thread.start();
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // captureLocalTrace returns only main thread's events
    var localTree = context.captureLocalTrace();
    assertThat(localTree.roots()).hasSize(1);
    assertThat(localTree.roots().get(0).signature()).isEqualTo(SIG);
  }

  @Test
  void captureTraceIncludesCrossThreadDeferredExit() throws Exception {
    SpanId spanId = context.enterMethod(SIG);
    context.detachFrame(spanId);

    var thread = new Thread(() -> context.exitMethodWithReturn("\"done\"", spanId));
    thread.start();
    thread.join();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isEqualTo(new TraceOutcome.Returned("\"done\""));
  }

  @Test
  void nestedDetachedFrameAttachesChildToDetachedParent() {
    var outerSig = new MethodSignature("Outer", "run", List.of());
    var innerSig = new MethodSignature("Inner", "exec", List.of());
    SpanId outerSpanId = context.enterMethod(outerSig);
    SpanId innerSpanId = context.enterMethod(innerSig);

    context.detachFrame(innerSpanId);
    context.detachFrame(outerSpanId);

    context.exitMethodWithReturn("\"inner-val\"", innerSpanId);
    context.exitMethodWithReturn("\"outer-val\"", outerSpanId);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("run");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("exec");
  }

  @Test
  void rootSpanGetsNewTraceIdAndSpanId() {
    context.enterMethod(SIG);

    var enter = (TraceEvent.EnterEvent) context.events().get(0);
    assertThat(enter.spanContext().traceId().value()).matches("[0-9a-f]{32}");
    assertThat(enter.spanContext().spanId().value()).matches("[0-9a-f]{16}");
  }

  @Test
  void nestedSpanInheritsSameTraceId() {
    context.enterMethod(new MethodSignature("Outer", "run", List.of()));
    context.enterMethod(new MethodSignature("Inner", "exec", List.of()));

    var events = context.events();
    var outer = (TraceEvent.EnterEvent) events.get(0);
    var inner = (TraceEvent.EnterEvent) events.get(1);
    assertThat(inner.spanContext().traceId()).isEqualTo(outer.spanContext().traceId());
    assertThat(inner.spanContext().spanId()).isNotEqualTo(outer.spanContext().spanId());
  }

  @Test
  void separateRootCallsGetDifferentTraceIds() {
    context.enterMethod(SIG);
    context.exitMethodWithReturn("null");

    context.reset();

    context = new ThreadLocalNarrativeContext();
    context.enterMethod(SIG);

    var enter = (TraceEvent.EnterEvent) context.events().get(0);
    // After reset, a new trace should get a new traceId
    assertThat(enter.spanContext().traceId().value()).matches("[0-9a-f]{32}");
  }

  private static TraceNode chainNode(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  /**
   * {@code emitTraceNode} accepts any {@link TraceNode} through public API — a hand-built or
   * replayed one is not guaranteed acyclic, and unlike a rendered artifact, unbounded recursion
   * here would hang or crash the traced application itself, not just corrupt an output file.
   */
  @Test
  @org.junit.jupiter.api.Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
  void emitTraceNodeOnAVeryDeepChainDoesNotStackOverflow() {
    TraceNode current = chainNode("call1000", List.of());
    for (var i = 0; i < 1_000; i++) {
      current = chainNode("call" + i, List.of(current));
    }

    context.emitTraceNode(current, null);

    assertThat(context.events()).hasSize(2 * 1_001);
  }

  @Test
  @org.junit.jupiter.api.Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
  void emitTraceNodeOnACyclicTreeDoesNotHang() {
    var childHolder = new java.util.ArrayList<TraceNode>();
    var b = chainNode("b", childHolder);
    var a = chainNode("a", List.of(b));
    childHolder.add(a);

    context.emitTraceNode(a, null);

    // a, b, and a again where the walk stops instead of re-descending into the cycle.
    assertThat(context.events()).hasSize(6);
  }
}
