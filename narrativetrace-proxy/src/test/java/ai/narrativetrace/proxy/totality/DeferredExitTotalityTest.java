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
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * A deferred exit never decides what the caller's future is or does.
 *
 * <p>INTENT: The 2026-09-01 bug hunt's future finding — a {@link CompletableFuture} subclass whose
 * {@code whenComplete} throws made the proxied call throw instead of returning the future. The
 * subclass path attaches the trace callback as a side effect on an object the proxy does not own,
 * so every part of that attachment is somebody else's code.
 */
class DeferredExitTotalityTest {

  interface AsyncService {
    CompletableFuture<String> load();

    HostileFuture loadHostile();
  }

  /** A future subclass that refuses callbacks — the report's repro. */
  static final class HostileFuture extends CompletableFuture<String> {
    @Override
    public CompletableFuture<String> whenComplete(
        BiConsumer<? super String, ? super Throwable> action) {
      throw new AssertionError("whenComplete must not be called by tracing");
    }
  }

  static final class AsyncServiceImpl implements AsyncService {
    private final HostileFuture hostile = new HostileFuture();
    private final CompletableFuture<String> plain = CompletableFuture.completedFuture("loaded");

    @Override
    public CompletableFuture<String> load() {
      return plain;
    }

    CompletableFuture<String> plain() {
      return plain;
    }

    @Override
    public HostileFuture loadHostile() {
      return hostile;
    }

    HostileFuture hostile() {
      return hostile;
    }
  }

  private static AsyncService proxyOver(AsyncServiceImpl target, TracingLevel level) {
    return NarrativeTraceProxy.trace(
        target,
        AsyncService.class,
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(level)));
  }

  @Test
  void aFutureSubclassThatRefusesCallbacksIsStillReturnedUnchanged() {
    var target = new AsyncServiceImpl();

    var returned = proxyOver(target, TracingLevel.DETAIL).loadHostile();

    assertThat(returned).isSameAs(target.hostile());
  }

  @Test
  void aFutureSubclassThatRefusesCallbacksStillCompletesForItsCaller() throws Exception {
    var target = new AsyncServiceImpl();
    var returned = proxyOver(target, TracingLevel.DETAIL).loadHostile();

    target.hostile().complete("done");

    assertThat(returned.get(5, TimeUnit.SECONDS)).isEqualTo("done");
  }

  @Test
  void aContextThatWillNotDetachTheFrameStillReturnsAUsableFuture() throws Exception {
    var target = new AsyncServiceImpl();
    var proxy =
        NarrativeTraceProxy.trace(
            target, AsyncService.class, new ProxyTotalityTest.SabotagedContext("detachFrame"));

    assertThat(proxy.load().get(5, TimeUnit.SECONDS)).isEqualTo("loaded");
  }

  @Test
  void aFrameThatCouldNotBeDetachedStillLeavesTheDeferredExitWired() {
    var target = new AsyncServiceImpl();
    var proxy =
        NarrativeTraceProxy.trace(
            target, AsyncService.class, new ProxyTotalityTest.SabotagedContext("detachFrame"));

    assertThat(proxy.load())
        .as("the ordering wrapper is still built, so joining it still implies the exit ran")
        .isNotSameAs(target.plain());
  }

  @Test
  void anExitHookThatThrowsAnErrorDoesNotBlockTheCallersJoin() throws Exception {
    var target = new AsyncServiceImpl();
    var proxy =
        NarrativeTraceProxy.trace(
            target,
            AsyncService.class,
            new ProxyTotalityTest.SabotagedContext("exitMethodWithReturn"));

    assertThat(proxy.load().get(5, TimeUnit.SECONDS)).isEqualTo("loaded");
  }

  @Test
  void anErrorExitHookThatThrowsDoesNotChangeTheFuturesOwnFailure() {
    var target = new AsyncServiceImpl();
    var proxy =
        NarrativeTraceProxy.trace(
            target,
            AsyncService.class,
            new ProxyTotalityTest.SabotagedContext("exitMethodWithException"));
    var failing = proxy.loadHostile();

    target.hostile().completeExceptionally(new IllegalStateException("async failed"));

    assertThatThrownBy(() -> failing.get(5, TimeUnit.SECONDS))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("async failed");
  }
}
