/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import io.micrometer.context.ContextRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Cross-package test: exercises the decorator exactly as a consumer would, through its public API
 * only.
 */
class ContextPropagatingTaskDecoratorTest {

  private static final MethodSignature CALLER_SPAN =
      new MethodSignature("OrderService", "placeOrder", List.of());

  private ThreadLocalNarrativeContext context;
  private NarrativeTraceThreadLocalAccessor accessor;
  private ExecutorService worker;

  @BeforeEach
  void setUp() {
    MDC.clear();
    context = new ThreadLocalNarrativeContext();
    accessor = new NarrativeTraceThreadLocalAccessor(context);
    ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
    worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "decorator-worker"));
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    worker.shutdownNow();
    worker.awaitTermination(5, TimeUnit.SECONDS);
    ContextRegistry.getInstance().removeThreadLocalAccessor(NarrativeTraceThreadLocalAccessor.KEY);
    context.reset();
    MDC.clear();
  }

  @Test
  void runsTheDelegate() throws Exception {
    var ran = new AtomicBoolean(false);

    runOnWorker(new ContextPropagatingTaskDecorator().decorate(() -> ran.set(true)));

    assertThat(ran).isTrue();
  }

  @Test
  void carriesTheCallerTraceOntoTheWorkerThread() throws Exception {
    context.enterMethod(CALLER_SPAN);
    var callerTraceId = context.traceId();
    var seen = new AtomicReference<TraceId>();

    runOnWorker(new ContextPropagatingTaskDecorator().decorate(() -> seen.set(context.traceId())));

    assertThat(seen.get()).isEqualTo(callerTraceId);
  }

  @Test
  void workDoneOnTheWorkerBecomesAChildOfTheSubmittingSpan() throws Exception {
    var callerSpan = context.enterMethod(CALLER_SPAN);
    var seen = new AtomicReference<SpanId>();

    runOnWorker(
        new ContextPropagatingTaskDecorator()
            .decorate(
                () -> {
                  context.enterMethod(new MethodSignature("Notifier", "notifyAsync", List.of()));
                  seen.set(context.currentSpanId());
                }));

    assertThat(callerSpan).isNotNull();
    assertThat(seen.get()).isNotNull().isNotEqualTo(callerSpan);
  }

  @Test
  void workerThreadStartsAnUnrelatedTraceWithoutTheDecorator() throws Exception {
    context.enterMethod(CALLER_SPAN);
    var callerTraceId = context.traceId();
    var seen = new AtomicReference<TraceId>();

    runOnWorker(() -> seen.set(context.traceId()));

    assertThat(seen.get()).isNotNull().isNotEqualTo(callerTraceId);
  }

  @Test
  void copiesTheCallerMdcOntoTheWorkerThread() throws Exception {
    MDC.put("traceId", "T-1");
    var seen = new AtomicReference<Map<String, String>>();

    runOnWorker(
        new ContextPropagatingTaskDecorator().decorate(() -> seen.set(MDC.getCopyOfContextMap())));

    assertThat(seen.get()).containsEntry("traceId", "T-1");
  }

  @Test
  void restoresTheWorkerThreadMdcAfterTheTask() throws Exception {
    MDC.put("traceId", "T-1");
    var task = new ContextPropagatingTaskDecorator().decorate(() -> {});

    var leftBehind = new AtomicReference<Map<String, String>>();
    worker
        .submit(
            () -> {
              MDC.put("worker", "own-value");
              task.run();
              leftBehind.set(MDC.getCopyOfContextMap());
            })
        .get(5, TimeUnit.SECONDS);

    assertThat(leftBehind.get()).containsExactly(Map.entry("worker", "own-value"));
  }

  @Test
  void clearsTheWorkerThreadMdcWhenItHadNone() throws Exception {
    MDC.put("traceId", "T-1");
    var task = new ContextPropagatingTaskDecorator().decorate(() -> {});

    var leftBehind = new AtomicReference<Map<String, String>>(Map.of("sentinel", "x"));
    worker
        .submit(
            () -> {
              task.run();
              leftBehind.set(MDC.getCopyOfContextMap());
            })
        .get(5, TimeUnit.SECONDS);

    assertThat(leftBehind.get()).isNull();
  }

  @Test
  void restoresTheWorkerThreadMdcWhenTheTaskThrows() throws Exception {
    MDC.put("traceId", "T-1");
    var task =
        new ContextPropagatingTaskDecorator()
            .decorate(
                () -> {
                  throw new IllegalStateException("boom");
                });

    var leftBehind = new AtomicReference<Map<String, String>>(Map.of("sentinel", "x"));
    var thrown = new AtomicReference<Throwable>();
    worker
        .submit(
            () -> {
              try {
                task.run();
              } catch (RuntimeException e) {
                thrown.set(e);
              }
              leftBehind.set(MDC.getCopyOfContextMap());
            })
        .get(5, TimeUnit.SECONDS);

    assertThat(thrown.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    assertThat(leftBehind.get()).isNull();
  }

  @Test
  void withMdcPropagationOffTheWorkerKeepsItsOwnMdc() throws Exception {
    MDC.put("traceId", "T-1");
    var seen = new AtomicReference<Map<String, String>>();

    runOnWorker(
        ContextPropagatingTaskDecorator.withoutMdc()
            .decorate(() -> seen.set(MDC.getCopyOfContextMap())));

    assertThat(seen.get()).isNull();
  }

  @Test
  void withMdcPropagationOffTheNarrativeContextStillTravels() throws Exception {
    context.enterMethod(CALLER_SPAN);
    var callerTraceId = context.traceId();
    var seen = new AtomicReference<TraceId>();

    runOnWorker(
        ContextPropagatingTaskDecorator.withoutMdc().decorate(() -> seen.set(context.traceId())));

    assertThat(seen.get()).isEqualTo(callerTraceId);
  }

  @Test
  void toleratesACallerWithAnEmptyMdc() throws Exception {
    var seen = new AtomicReference<Map<String, String>>(Map.of("sentinel", "x"));

    runOnWorker(
        new ContextPropagatingTaskDecorator().decorate(() -> seen.set(MDC.getCopyOfContextMap())));

    assertThat(seen.get()).isNull();
  }

  @Test
  void rejectsANullRunnable() {
    assertThatThrownBy(() -> new ContextPropagatingTaskDecorator().decorate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runnable");
  }

  @Test
  void capturesTheContextAtDecorationTimeNotAtRunTime() throws Exception {
    context.enterMethod(CALLER_SPAN);
    var atDecoration = context.traceId();
    var seen = new AtomicReference<TraceId>();
    var observing =
        new ContextPropagatingTaskDecorator().decorate(() -> seen.set(context.traceId()));
    context.reset();

    runOnWorker(observing);

    assertThat(seen.get()).isEqualTo(atDecoration);
  }

  private void runOnWorker(Runnable task) throws Exception {
    worker.submit(task).get(5, TimeUnit.SECONDS);
  }
}
