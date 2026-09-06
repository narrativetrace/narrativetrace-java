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
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The release gate for the no-poison contract on the proxy path: one test row per line of the
 * supplemental bug hunt's probe output.
 *
 * <p>INTENT: The supplement's {@code SupplementalFaultProbe} swept every {@link TracingLevel} with
 * a hostile parameter and two hostile return shapes and found only {@code OFF} clean. This file is
 * that sweep, kept as a block in the gate output so the matrix cannot quietly lose a row. Sibling
 * files cover the same contract from the other end: {@code ProxyTotalityTest} sabotages the context
 * hooks one at a time, {@code DeferredExitTotalityTest} the future paths.
 *
 * <p><b>@llmNote</b> The assertion is always the same two facts, because they are the contract: the
 * target ran exactly once, and the caller received the target's own value. A trace that is missing
 * or degraded is an acceptable outcome here and is deliberately not asserted — the trace is the
 * thing allowed to lose.
 */
class NoPoisonContractTest {

  interface OrderService {
    String accept(Collection<String> items);

    Object produceCollection();

    Object produceNumber();

    String failBusiness();

    String failWithError();
  }

  /** The business failure a caller must receive unchanged. */
  static final class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    BusinessException(String message) {
      super(message);
    }
  }

  /** The same, outside {@code Exception}: the half of the hierarchy the old boundary missed. */
  static final class BusinessError extends Error {
    private static final long serialVersionUID = 1L;

    BusinessError(String message) {
      super(message);
    }
  }

  /** A collection that throws an {@link Error} from everything a renderer touches. */
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

  /** The other shape the supplement used: an ordinary JDK type with a throwing {@code toString}. */
  static final class ExplodingNumber extends Number {
    private static final long serialVersionUID = 1L;

    @Override
    public int intValue() {
      return 1;
    }

    @Override
    public long longValue() {
      return 1L;
    }

    @Override
    public float floatValue() {
      return 1.0f;
    }

    @Override
    public double doubleValue() {
      return 1.0d;
    }

    @Override
    public String toString() {
      throw new AssertionError("number toString must not be touched by tracing");
    }
  }

  static final class RealOrderService implements OrderService {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String accept(Collection<String> items) {
      calls.incrementAndGet();
      return "ok";
    }

    @Override
    public Object produceCollection() {
      calls.incrementAndGet();
      return new ExplodingCollection();
    }

    @Override
    public Object produceNumber() {
      calls.incrementAndGet();
      return new ExplodingNumber();
    }

    @Override
    public String failBusiness() {
      calls.incrementAndGet();
      throw new BusinessException("business failure");
    }

    @Override
    public String failWithError() {
      calls.incrementAndGet();
      throw new BusinessError("business error");
    }

    int calls() {
      return calls.get();
    }
  }

  private static OrderService proxyAt(RealOrderService target, TracingLevel level) {
    return proxyOver(target, new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level)));
  }

  private static OrderService proxyOver(RealOrderService target, NarrativeContext context) {
    return NarrativeTraceProxy.trace(target, OrderService.class, context);
  }

  /**
   * A real context with one hook replaced by a throw — the supplement's own mechanism, a dynamic
   * proxy over {@link NarrativeContext}, so every overload of the named hook is poisoned at once
   * and no delegating stub can drift from the interface it stands in for.
   */
  private static NarrativeContext contextThrowingFrom(TracingLevel level, String hook) {
    NarrativeContext delegate = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level));
    return (NarrativeContext)
        Proxy.newProxyInstance(
            NarrativeContext.class.getClassLoader(),
            new Class<?>[] {NarrativeContext.class},
            (proxy, method, args) -> {
              if (method.getName().equals(hook)) {
                throw new AssertionError(hook + " must not decide the call's outcome");
              }
              try {
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aHostileParameterStillRunsTheTargetAtEveryLevel(TracingLevel level) {
    var target = new RealOrderService();

    var result = proxyAt(target, level).accept(new ExplodingCollection());

    assertThat(result).as("the target's own return value at %s", level).isEqualTo("ok");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aHostileCollectionReturnReachesTheCallerAtEveryLevel(TracingLevel level) {
    var target = new RealOrderService();

    var result = proxyAt(target, level).produceCollection();

    assertThat(result)
        .as("the target's own value at %s", level)
        .isInstanceOf(ExplodingCollection.class);
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aHostileNumberReturnReachesTheCallerAtEveryLevel(TracingLevel level) {
    var target = new RealOrderService();

    var result = proxyAt(target, level).produceNumber();

    assertThat(result)
        .as("the target's own value at %s", level)
        .isInstanceOf(ExplodingNumber.class);
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  // ---------------------------------------------------------- S2: exits never replace outcomes

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aThrowingExitHookDoesNotReplaceTheBusinessResult(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextThrowingFrom(level, "exitMethodWithReturn"));

    var result = proxy.accept(List.of("a"));

    assertThat(result).as("the target's own return value at %s", level).isEqualTo("ok");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aThrowingExitHookDoesNotReplaceTheBusinessException(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextThrowingFrom(level, "exitMethodWithException"));

    assertThatThrownBy(proxy::failBusiness)
        .as("the caller's own failure at %s", level)
        .isInstanceOf(BusinessException.class)
        .hasMessage("business failure");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aThrowingExitHookDoesNotReplaceABusinessError(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextThrowingFrom(level, "exitMethodWithException"));

    assertThatThrownBy(proxy::failWithError)
        .as("an Error is the caller's outcome too, at %s", level)
        .isInstanceOf(BusinessError.class)
        .hasMessage("business error");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  /**
   * A real context whose {@code runScoped} runs the supplier and then throws from its own {@code
   * finally} — the one shape a custom context can use to replace a return value the target already
   * produced. The 2026-09-01 clean bug hunt built this probe; it lives here so the carve-out is a
   * decision with a test behind it rather than an accident nobody re-reads.
   */
  private static NarrativeContext contextPoisoningRunScopedAfterTheTarget(TracingLevel level) {
    NarrativeContext delegate = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level));
    return (NarrativeContext)
        Proxy.newProxyInstance(
            NarrativeContext.class.getClassLoader(),
            new Class<?>[] {NarrativeContext.class},
            (proxy, method, args) -> {
              if (method.getName().equals("runScoped")) {
                try {
                  method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                  // Discarded on purpose: a poisoning `finally` loses the target's outcome too.
                }
                throw new AssertionError("runScoped finally poisoned");
              }
              try {
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  /** The same context, refusing before the target is ever reached. */
  private static NarrativeContext contextPoisoningRunScopedBeforeTheTarget(TracingLevel level) {
    NarrativeContext delegate = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level));
    return (NarrativeContext)
        Proxy.newProxyInstance(
            NarrativeContext.class.getClassLoader(),
            new Class<?>[] {NarrativeContext.class},
            (proxy, method, args) -> {
              if (method.getName().equals("runScoped")) {
                throw new AssertionError("runScoped refused before the target ran");
              }
              try {
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  // ------------------------------------------- The one boundary that is host code, not tracing

  /**
   * DECIDED, 2026-09-01: a custom {@code runScoped} is inside the guarantee's boundary, and this
   * test is the boundary marker. The proxy cannot re-invoke the target (duplicate side effects) and
   * cannot invent a return value, so the throwable wins — even though the target already produced
   * one. Overturning this decision means changing this test first.
   */
  @ParameterizedTest
  @EnumSource(value = TracingLevel.class, names = "OFF", mode = EnumSource.Mode.EXCLUDE)
  void aCustomRunScopedThatThrowsAfterTheTargetRanReplacesTheResult(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextPoisoningRunScopedAfterTheTarget(level));

    assertThatThrownBy(() -> proxy.accept(List.of("a")))
        .as("a custom runScoped is host code, and its failure is the call's outcome at %s", level)
        .isInstanceOf(AssertionError.class)
        .hasMessage("runScoped finally poisoned");
    assertThat(target.calls()).as("the target still ran exactly once at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(value = TracingLevel.class, names = "OFF", mode = EnumSource.Mode.EXCLUDE)
  void aCustomRunScopedThatThrowsBeforeTheTargetRanKeepsItFromRunning(TracingLevel level) {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextPoisoningRunScopedBeforeTheTarget(level));

    assertThatThrownBy(() -> proxy.accept(List.of("a")))
        .as("nothing invents a return value for a target that never spoke, at %s", level)
        .isInstanceOf(AssertionError.class)
        .hasMessage("runScoped refused before the target ran");
    assertThat(target.calls()).as("the target was never invoked at %s", level).isZero();
  }

  /**
   * {@code OFF} is excluded from the two tests above because at {@code OFF} the seam is never
   * reached: {@code isActive()} is false, the proxy invokes the target raw, and the context's
   * {@code runScoped} is not consulted at all. Asserted rather than assumed — the exclusion is only
   * honest if the reason for it is true.
   */
  @Test
  void atOffAHostileRunScopedIsNeverConsulted() {
    var target = new RealOrderService();
    var proxy = proxyOver(target, contextPoisoningRunScopedAfterTheTarget(TracingLevel.OFF));

    var result = proxy.accept(List.of("a"));

    assertThat(result).as("OFF does not enter the scope at all").isEqualTo("ok");
    assertThat(target.calls()).isOne();
  }

  /**
   * The other half of the boundary, and the half that is a guarantee: the contexts this library
   * ships never do what the two tests above describe. {@link ThreadLocalNarrativeContext#runScoped}
   * restores the previous scope in its {@code finally} — a field write that cannot throw.
   */
  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void theBuiltInContextNeverReplacesASuccessfulReturnFromItsOwnScoping(TracingLevel level) {
    var target = new RealOrderService();

    var result = proxyAt(target, level).accept(List.of("a"));

    assertThat(result).as("the target's own return value at %s", level).isEqualTo("ok");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void theBuiltInContextNeverReplacesABusinessFailureFromItsOwnScoping(TracingLevel level) {
    var target = new RealOrderService();

    assertThatThrownBy(() -> proxyAt(target, level).failBusiness())
        .as("the caller's own failure at %s", level)
        .isInstanceOf(BusinessException.class)
        .hasMessage("business failure");
    assertThat(target.calls()).as("target invocations at %s", level).isOne();
  }

  @Test
  void theTracingFailureIsDroppedRatherThanAttachedToTheBusinessException() {
    var target = new RealOrderService();
    var proxy =
        proxyOver(target, contextThrowingFrom(TracingLevel.DETAIL, "exitMethodWithException"));

    assertThatThrownBy(proxy::failBusiness)
        .isInstanceOf(BusinessException.class)
        .satisfies(t -> assertThat(t.getSuppressed()).isEmpty());
  }
}
