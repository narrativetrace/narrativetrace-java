/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SessionId;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves that one request's lifecycle operations on a shared context never corrupt another
 * in-flight request on a different thread.
 *
 * <p>Regression tests for the concurrent-request corruption defect: {@code reset()} used to clear
 * the context-global span-context map and pipeline store, destroying every other in-flight
 * request's trace.
 */
class ConcurrentRequestIsolationTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
  }

  @Test
  void resetOnAnotherThreadDoesNotDestroyThisThreadsActiveTrace() throws Exception {
    var entered = new CountDownLatch(1);
    var resetDone = new CountDownLatch(1);
    var captured = new AtomicReference<TraceTree>();
    var failure = new AtomicReference<Throwable>();

    var requestA = new Thread(tracedRequest(entered, resetDone, captured, failure));
    requestA.start();
    awaitOrFail(entered);

    context.reset(); // request B finishing on another thread must not touch A's state

    resetDone.countDown();
    requestA.join(5000);

    assertThat(failure.get()).isNull();
    assertReturnedOk(captured.get());
  }

  private Runnable tracedRequest(
      CountDownLatch entered,
      CountDownLatch proceed,
      AtomicReference<TraceTree> captured,
      AtomicReference<Throwable> failure) {
    return () -> {
      try {
        context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
        entered.countDown();
        awaitOrFail(proceed);
        context.exitMethodWithReturn("\"ok\"");
        captured.set(context.captureTrace());
      } catch (Throwable t) { // NOPMD - test must surface any worker failure
        failure.set(t);
      }
    };
  }

  @Test
  void requestMetadataIsIsolatedBetweenConcurrentRequests() throws Exception {
    var stampedOnWorker = new CountDownLatch(1);
    var stampedOnMain = new CountDownLatch(1);
    var workerSpan = new AtomicReference<SpanContext>();

    var requestA =
        new Thread(
            () -> {
              context.setUserContext(
                  EnduserId.of("user-a"), SessionId.of("sess-a"), TenantId.of("tenant-a"));
              stampedOnWorker.countDown();
              awaitOrFail(stampedOnMain);
              workerSpan.set(traceOneCallAndReadSpanContext());
            });
    requestA.start();
    awaitOrFail(stampedOnWorker);

    context.setUserContext(EnduserId.of("user-b"), SessionId.of("sess-b"), TenantId.of("tenant-b"));

    stampedOnMain.countDown();
    requestA.join(5000);

    assertThat(workerSpan.get()).isNotNull();
    assertThat(workerSpan.get().tenantId()).isEqualTo(TenantId.of("tenant-a"));
    assertThat(workerSpan.get().enduserId()).isEqualTo(EnduserId.of("user-a"));
  }

  @Test
  void snapshotCarriesRequestMetadataToWorkerThread() throws Exception {
    context.setUserContext(EnduserId.of("user-a"), SessionId.of("sess-a"), TenantId.of("tenant-a"));
    var snapshot = context.snapshot();
    var workerSpan = new AtomicReference<SpanContext>();

    var worker = new Thread(snapshot.wrap(() -> workerSpan.set(traceOneCallAndReadSpanContext())));
    worker.start();
    worker.join(5000);

    assertThat(workerSpan.get()).isNotNull();
    assertThat(workerSpan.get().tenantId()).isEqualTo(TenantId.of("tenant-a"));
    assertThat(workerSpan.get().enduserId()).isEqualTo(EnduserId.of("user-a"));
  }

  private SpanContext traceOneCallAndReadSpanContext() {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("\"ok\"");
    return context.events().stream()
        .filter(e -> e instanceof TraceEvent.EnterEvent)
        .map(e -> ((TraceEvent.EnterEvent) e).spanContext())
        .findFirst()
        .orElse(null);
  }

  private static void assertReturnedOk(TraceTree tree) {
    assertThat(tree).isNotNull();
    assertThat(tree.roots()).hasSize(1);
    var outcome = tree.roots().get(0).outcome();
    assertThat(outcome).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) outcome).renderedValue()).isEqualTo("\"ok\"");
  }

  private static void awaitOrFail(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as("latch released in time").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
