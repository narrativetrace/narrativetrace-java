/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Proves that a caller joining a deferred-exit future can never hang because trace recording
 * failed.
 *
 * <p>Regression tests for the deferred-exit hang: the {@code whenComplete} callback recorded the
 * exit <em>before</em> completing the wrapper, so an exception escaping the recording step left the
 * application's {@code join()}/{@code get()} waiting forever.
 */
class DeferredExitResilienceTest {

  interface AsyncService {
    CompletableFuture<String> work();
  }

  static class AsyncServiceImpl implements AsyncService {
    final CompletableFuture<String> future = new CompletableFuture<>();

    @Override
    public CompletableFuture<String> work() {
      return future;
    }
  }

  @Test
  void deferredFutureCompletesEvenWhenPipelineListenerThrows() throws Exception {
    var context =
        new ThreadLocalNarrativeContext(
            new NarrativeTraceConfig(),
            new DualPathPipeline(
                event -> {
                  throw new IllegalStateException("listener down");
                },
                new BufferedEventConsumer(1024, false)));
    var impl = new AsyncServiceImpl();
    var service = NarrativeTraceProxy.trace(impl, AsyncService.class, context);

    var wrapper = service.work();
    impl.future.complete("done");

    assertThat(wrapper.get(2, TimeUnit.SECONDS)).isEqualTo("done");
  }

  @Test
  void deferredFutureCompletesEvenWhenExitRecordingThrows() throws Exception {
    var impl = new AsyncServiceImpl();
    var service = NarrativeTraceProxy.trace(impl, AsyncService.class, new ExitFailingContext());

    var wrapper = service.work();
    impl.future.complete("done");

    assertThat(wrapper.get(2, TimeUnit.SECONDS)).isEqualTo("done");
  }

  @Test
  void deferredFuturePropagatesOriginalFailureEvenWhenExitRecordingThrows() {
    var impl = new AsyncServiceImpl();
    var service = NarrativeTraceProxy.trace(impl, AsyncService.class, new ExitFailingContext());

    var wrapper = service.work();
    impl.future.completeExceptionally(new IllegalArgumentException("business failure"));

    assertThatThrownBy(() -> wrapper.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("business failure");
  }

  /** Delegating context whose deferred-exit recording methods always fail. */
  static final class ExitFailingContext implements NarrativeContext {
    private final ThreadLocalNarrativeContext delegate = new ThreadLocalNarrativeContext();

    @Override
    public void exitMethodWithReturn(
        String renderedReturnValue, RenderedValue structuredReturnValue, SpanId spanId) {
      throw new IllegalStateException("exit recording failed");
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
      throw new IllegalStateException("exit recording failed");
    }

    @Override
    public boolean isActive() {
      return delegate.isActive();
    }

    @Override
    public SpanId enterMethod(MethodSignature signature) {
      return delegate.enterMethod(signature);
    }

    @Override
    public void detachFrame(SpanId spanId) {
      delegate.detachFrame(spanId);
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue) {
      delegate.exitMethodWithReturn(renderedReturnValue);
    }

    @Override
    public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {
      delegate.exitMethodWithReturn(renderedReturnValue, spanId);
    }

    @Override
    public void exitMethodWithException(Throwable exception, String errorContext) {
      delegate.exitMethodWithException(exception, errorContext);
    }

    @Override
    public <T> T runScoped(SpanId spanId, Supplier<T> fn) {
      return delegate.runScoped(spanId, fn);
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
}
