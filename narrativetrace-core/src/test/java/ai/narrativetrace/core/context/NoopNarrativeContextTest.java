/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoopNarrativeContextTest {

  @Test
  void allMethodsNoOpAndTraceIsEmpty() {
    var context = NoopNarrativeContext.INSTANCE;

    context.enterMethod(new MethodSignature("Any", "method", List.of()));
    context.exitMethodWithReturn("value");
    context.exitMethodWithException(new RuntimeException(), null);
    context.reset();

    var tree = context.captureTrace();
    assertThat(tree.isEmpty()).isTrue();
    assertThat(tree.roots()).isEmpty();
  }

  @Test
  void isActiveReturnsFalse() {
    assertThat(NoopNarrativeContext.INSTANCE.isActive()).isFalse();
  }

  @Test
  void isSingleton() {
    assertThat(NoopNarrativeContext.INSTANCE).isSameAs(NoopNarrativeContext.INSTANCE);
  }

  @Test
  void noopWrapRunnableDelegatesDirectly() {
    var context = NoopNarrativeContext.INSTANCE;
    var snapshot = context.snapshot();

    boolean[] ran = {false};
    Runnable original = () -> ran[0] = true;
    var wrapped = snapshot.wrap(original);
    wrapped.run();

    assertThat(ran[0]).isTrue();
  }

  @Test
  void currentSpanIdReturnsNull() {
    assertThat(NoopNarrativeContext.INSTANCE.currentSpanId()).isNull();
  }

  @Test
  void runScopedExecutesSupplierAndReturnsResult() {
    var result =
        NoopNarrativeContext.INSTANCE.runScoped(SpanId.of("0123456789abcdef"), () -> "hello");
    assertThat(result).isEqualTo("hello");
  }

  @Test
  void detachFrameIsNoOp() {
    NoopNarrativeContext.INSTANCE.detachFrame(SpanId.of("0123456789abcdef"));
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void exitMethodWithReturnBySpanIdIsNoOp() {
    NoopNarrativeContext.INSTANCE.exitMethodWithReturn("value", SpanId.of("0123456789abcdef"));
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void exitMethodWithExceptionBySpanIdIsNoOp() {
    NoopNarrativeContext.INSTANCE.exitMethodWithException(
        new RuntimeException(), null, SpanId.of("0123456789abcdef"));
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void beginScopeReturnsNull() {
    assertThat(NoopNarrativeContext.INSTANCE.beginScope(SpanId.of("0123456789abcdef"))).isNull();
  }

  @Test
  void endScopeIsNoOp() {
    NoopNarrativeContext.INSTANCE.endScope(SpanId.of("0123456789abcdef"));
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void emitTraceNodeIsNoOp() {
    var sig = new MethodSignature("Svc", "run", List.of());
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned(null));
    NoopNarrativeContext.INSTANCE.emitTraceNode(node, null);
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void onFireAndForgetLaunchedIsNoOp() {
    NoopNarrativeContext.INSTANCE.onFireAndForgetLaunched("g1");
    assertThat(NoopNarrativeContext.INSTANCE.captureTrace().isEmpty()).isTrue();
  }

  /**
   * The default collect is the capture-then-discard pair, verbatim: an implementation that
   * overrides neither still hands the caller its copy, and one that overrides only the halves is
   * still routed through them.
   */
  @Test
  void defaultCollectLocalTraceCapturesThenForgets() {
    var tree = NoopNarrativeContext.INSTANCE.collectLocalTrace();

    assertThat(tree.isEmpty()).isTrue();
    assertThat(tree.roots()).isEmpty();
  }

  @Test
  void defaultIsActiveReturnsTrue() {
    NarrativeContext defaultImpl = new MinimalNarrativeContext();
    assertThat(defaultImpl.isActive()).isTrue();
  }

  @Test
  void traceIdReturnsNonNull() {
    assertThat(NoopNarrativeContext.INSTANCE.traceId()).isNotNull();
  }

  @Test
  void traceIdReturnsValidW3cFormat() {
    assertThat(NoopNarrativeContext.INSTANCE.traceId().toString()).matches("[0-9a-f]{32}");
  }

  @Test
  void snapshotReturnsNonNullAndActivateReturnsScope() {
    var context = NoopNarrativeContext.INSTANCE;

    var snapshot = context.snapshot();
    assertThat(snapshot).isNotNull();

    var scope = snapshot.activate();
    assertThat(scope).isNotNull();
    scope.close(); // should not throw
  }

  private static class MinimalNarrativeContext implements NarrativeContext {
    @Override
    public SpanId enterMethod(MethodSignature signature) {
      return null;
    }

    @Override
    public void detachFrame(SpanId spanId) {}

    @Override
    public void exitMethodWithReturn(String renderedReturnValue) {}

    @Override
    public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {}

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext) {}

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {}

    @Override
    public TraceTree captureTrace() {
      return null;
    }

    @Override
    public void reset() {}

    @Override
    public ContextSnapshot snapshot() {
      return null;
    }
  }
}
