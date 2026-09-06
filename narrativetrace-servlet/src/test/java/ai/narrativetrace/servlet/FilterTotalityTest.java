/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * A request the application handled is never turned into a failure by tracing.
 *
 * <p>INTENT: The 2026-09-01 bug hunt watched {@code context.reset()} and an exporter raising {@code
 * AssertionError} both propagate out of {@code doFilter}. The cleanup runs in the chain's {@code
 * finally}, which is the worst possible place for a throw: it replaces whatever the application
 * produced, success or failure.
 */
class FilterTotalityTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  /** Everything a filter asks of a context, forwarded to a real one so subclasses sabotage one. */
  private static class DelegatingContext implements NarrativeContext {
    final ThreadLocalNarrativeContext delegate = new ThreadLocalNarrativeContext();

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
    public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {
      delegate.exitMethodWithException(exception, errorContext, spanId);
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

  /** A context whose reset throws, counting how often it was attempted. */
  private static final class ResetFailingContext extends DelegatingContext {
    private final AtomicInteger resets = new AtomicInteger();

    @Override
    public void reset() {
      resets.incrementAndGet();
      throw new AssertionError("reset must not fail the request");
    }
  }

  /** A context that will not produce a tree, but resets normally. */
  private static final class CaptureFailingContext extends DelegatingContext {
    private final AtomicInteger resets = new AtomicInteger();

    @Override
    public TraceTree captureTrace() {
      throw new AssertionError("captureTrace must not fail the request");
    }

    @Override
    public void reset() {
      resets.incrementAndGet();
      super.reset();
    }
  }

  private static final FilterChain DOES_NOTHING = (req, res) -> {};

  @Test
  void anExporterThrowingAnErrorDoesNotFailTheRequest() {
    var context = new ThreadLocalNarrativeContext();
    var filter =
        new NarrativeTraceFilter(
            context,
            (tree, req) -> {
              throw new AssertionError("exporter must not fail the request");
            });

    assertThatNoException()
        .isThrownBy(
            () ->
                filter.doFilter(
                    new StubHttpServletRequest("GET", "/api/test"),
                    new StubHttpServletResponse(),
                    tracingChain(context)));
  }

  @Test
  void aContextResetThatThrowsDoesNotFailTheRequest() {
    var filter = new NarrativeTraceFilter(new ResetFailingContext(), (tree, req) -> {});

    assertThatNoException()
        .isThrownBy(
            () ->
                filter.doFilter(
                    new StubHttpServletRequest("GET", "/api/test"),
                    new StubHttpServletResponse(),
                    DOES_NOTHING));
  }

  @Test
  void aCaptureThatThrowsStillLetsTheContextReset() throws Exception {
    var context = new CaptureFailingContext();
    var filter = new NarrativeTraceFilter(context, (tree, req) -> {});

    filter.doFilter(
        new StubHttpServletRequest("GET", "/api/test"),
        new StubHttpServletResponse(),
        DOES_NOTHING);

    assertThat(context.resets.get()).as("reset before the chain and after it").isEqualTo(2);
  }

  @Test
  void aFailingChainKeepsItsOwnFailureWhenExportAlsoFails() {
    var filter =
        new NarrativeTraceFilter(
            new ResetFailingContext(),
            (tree, req) -> {
              throw new AssertionError("exporter must not fail the request");
            });
    FilterChain failing =
        (req, res) -> {
          throw new ServletException("the application failed");
        };

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new StubHttpServletRequest("GET", "/api/test"),
                    new StubHttpServletResponse(),
                    failing))
        .isInstanceOf(ServletException.class)
        .hasMessage("the application failed");
  }

  /** A chain that records something, so the exporter actually has a tree to fail on. */
  private static FilterChain tracingChain(ThreadLocalNarrativeContext context) {
    return (req, res) -> {
      context.enterMethod(new MethodSignature("Handler", "handle", List.of()));
      context.exitMethodWithReturn("\"ok\"");
    };
  }
}
