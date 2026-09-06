/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy.totality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The proxy never lets tracing decide what a call returns or throws.
 *
 * <p>INTENT: Encodes the 2026-09-01 bug hunt's proxy P0 and its supplemental S1/S2 — a hostile
 * parameter that stopped the target from running, a hostile return value that replaced a successful
 * result at every active level, and context hooks whose failure replaced both the result and the
 * business exception. Written from a package other than the proxy's own, so the cross-package
 * reflection path is the one under test.
 */
class ProxyTotalityTest {

  interface OrderService {
    String handle(Collection<String> items);

    Collection<String> produce();

    String fail();

    void record(String id);
  }

  /** A collection that throws from everything the renderer touches. */
  static final class ExplodingCollection extends AbstractCollection<String> {
    @Override
    public Iterator<String> iterator() {
      throw new AssertionError("iterator must not be touched by tracing");
    }

    @Override
    public int size() {
      throw new AssertionError("size must not be touched by tracing");
    }
  }

  static final class RealOrderService implements OrderService {
    private int calls;

    @Override
    public String handle(Collection<String> items) {
      calls++;
      return "ok";
    }

    @Override
    public Collection<String> produce() {
      calls++;
      return new ExplodingCollection();
    }

    @Override
    public String fail() {
      calls++;
      throw new IllegalStateException("business failed");
    }

    @Override
    public void record(String id) {
      calls++;
    }

    int calls() {
      return calls;
    }
  }

  /**
   * A context that delegates everything to a real one except the hooks named at construction, which
   * throw an {@link Error}.
   */
  static final class SabotagedContext implements NarrativeContext {
    private final NarrativeContext delegate = new ThreadLocalNarrativeContext();
    private final String sabotaged;

    SabotagedContext(String sabotaged) {
      this.sabotaged = sabotaged;
    }

    private <T> T check(String hook, Supplier<T> body) {
      if (sabotaged.equals(hook)) {
        throw new AssertionError(hook + " must not decide the call's outcome");
      }
      return body.get();
    }

    @Override
    public boolean isActive() {
      return check("isActive", delegate::isActive);
    }

    @Override
    public boolean capturesParameterValues() {
      return check("capturesParameterValues", delegate::capturesParameterValues);
    }

    @Override
    public boolean capturesInstanceIds() {
      return check("capturesInstanceIds", delegate::capturesInstanceIds);
    }

    @Override
    public SpanId enterMethod(MethodSignature signature) {
      return check("enterMethod", () -> delegate.enterMethod(signature));
    }

    @Override
    public void detachFrame(SpanId spanId) {
      check(
          "detachFrame",
          () -> {
            delegate.detachFrame(spanId);
            return null;
          });
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue) {
      exitMethodWithReturn(renderedReturnValue, null);
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {
      exitMethodWithReturn(renderedReturnValue, null, spanId);
    }

    @Override
    public void exitMethodWithReturn(
        String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
      check(
          "exitMethodWithReturn",
          () -> {
            delegate.exitMethodWithReturn(renderedReturnValue, structuredReturnValue, spanId);
            return null;
          });
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext) {
      exitMethodWithException(exception, errorContext, null);
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
      check(
          "exitMethodWithException",
          () -> {
            delegate.exitMethodWithException(exception, errorContext, spanId);
            return null;
          });
    }

    @Override
    public <T> T runScoped(SpanId spanId, Supplier<T> fn) {
      return delegate.runScoped(spanId, fn);
    }

    @Override
    public SpanId beginScope(SpanId spanId) {
      return delegate.beginScope(spanId);
    }

    @Override
    public void endScope(SpanId previousScope) {
      delegate.endScope(previousScope);
    }

    @Override
    public TraceTree captureTrace() {
      return delegate.captureTrace();
    }

    @Override
    public void reset() {
      delegate.reset();
    }

    @Override
    public ContextSnapshot snapshot() {
      return delegate.snapshot();
    }
  }

  private static OrderService proxyOver(RealOrderService target, NarrativeContext context) {
    return NarrativeTraceProxy.trace(target, OrderService.class, context);
  }

  // ------------------------------------------------------------------ hostile values, real context

  @Test
  void aHostileParameterStillRunsTheTargetAndReturnsItsValue() {
    var target = new RealOrderService();
    var proxy =
        proxyOver(
            target, new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL)));

    assertThat(proxy.handle(new ExplodingCollection())).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  @ParameterizedTest
  @EnumSource(
      value = TracingLevel.class,
      names = {"ERRORS", "SUMMARY", "NARRATIVE", "DETAIL"})
  void aHostileReturnValueReachesTheCallerAtEveryActiveLevel(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level)));

    assertThat(proxy.produce()).isInstanceOf(ExplodingCollection.class);
    assertThat(target.calls()).isOne();
  }

  @Test
  void aBusinessErrorReachesTheCallerUnchangedAndIsStillRecorded() {
    var target = new RealOrderService();
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    var proxy = proxyOver(target, context);

    assertThatThrownBy(proxy::fail)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("business failed");
    assertThat(context.captureTrace().roots().get(0).outcome())
        .isInstanceOf(ai.narrativetrace.api.event.TraceOutcome.Threw.class);
  }

  // ------------------------------------------------------------------ hostile context hooks

  @Test
  void anExitHookThatThrowsDoesNotReplaceTheBusinessResult() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("exitMethodWithReturn"));

    assertThat(proxy.handle(java.util.List.of("a"))).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  @Test
  void anExitHookThatThrowsDoesNotReplaceTheBusinessException() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("exitMethodWithException"));

    assertThatThrownBy(proxy::fail)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("business failed");
    assertThat(target.calls()).isOne();
  }

  @Test
  void aVoidExitHookThatThrowsDoesNotFailTheCall() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("exitMethodWithReturn"));

    proxy.record("A-1");

    assertThat(target.calls()).isOne();
  }

  @Test
  void anEnterHookThatThrowsStillRunsTheTargetExactlyOnce() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("enterMethod"));

    assertThat(proxy.handle(java.util.List.of("a"))).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  @Test
  void aContextThatWillNotSayWhetherItIsActiveStillRunsTheTarget() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("isActive"));

    assertThat(proxy.handle(java.util.List.of("a"))).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  @Test
  void aSignatureThatCannotBeBuiltStillRunsTheTarget() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("capturesParameterValues"));

    assertThat(proxy.handle(java.util.List.of("a"))).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  @Test
  void anInstanceIdHookThatThrowsStillRunsTheTarget() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, new SabotagedContext("capturesInstanceIds"));

    assertThat(proxy.handle(java.util.List.of("a"))).isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }
}
