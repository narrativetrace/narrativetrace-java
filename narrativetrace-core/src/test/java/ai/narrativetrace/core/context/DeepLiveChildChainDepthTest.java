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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-26-1 — capturing a trace may never throw into the traced application: the live-child
 * walk recurses once per open async scope, and nothing bounds how many of those one thread can
 * hold, so a deep chain turns {@code captureTrace()} into a {@link StackOverflowError} on the
 * caller's thread.
 *
 * <p>INTENT: {@code TraceStack.liveChildSpanIds()} and {@code TraceStack.reportableSpanIds()} are
 * mutually recursive, and the {@code @llmNote} on the former argues the recursion is safe because
 * "depth is the nesting depth of <em>simultaneously open</em> async scopes, which is bounded by
 * live threads". That premise does not hold: one thread may hold arbitrarily many open scopes at
 * once, because {@code ContextSnapshot.activate()} hands back a {@code ContextScope} handle a
 * framework is free to store and close later (a per-subscription or per-request registry), and
 * every activation registers the new stack as a live child of the one it displaced. The registry
 * caps <em>breadth</em> — {@code registerLiveChild} refuses past {@code MAX_ADOPTED_SPANS} children
 * of one stack — and caps nothing at all in depth.
 *
 * <p><b>@llmNote</b> This is the master invariant, not a cosmetic one. Every other walk in this
 * repository is bounded ({@code TreeWalk.MAX_DEPTH}, the adoption cap, the ring buffer); this one
 * is not, and the thread it overflows is the application's own — the error leaves the library
 * through a public {@code captureTrace()} call and is not an error the caller can be expected to
 * handle. Leaking scope handles is exactly the failure mode the weak references here were written
 * to survive, and surviving it means degrading, not crashing.
 */
class DeepLiveChildChainDepthTest {

  /**
   * Open scopes on the worker thread. Deep enough to exhaust a default thread stack, shallow enough
   * that building the chain stays well inside the test heap — each rung is one empty {@code
   * TraceStack} and one {@code ContextScope} handle.
   */
  private static final int OPEN_SCOPES = 100_000;

  private static final MethodSignature ORIGIN_CALL =
      new MethodSignature("OrderService", "placeOrder", List.of());

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();
  private final CountDownLatch chainBuilt = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private Thread worker;

  @AfterEach
  void releaseTheWorker() throws Exception {
    release.countDown();
    if (worker != null) {
      worker.join();
    }
    context.reset();
  }

  @Test
  @DisplayName(
      "ADV-2026-10-26-1: a capture over many open async scopes must not throw into the host")
  void aDeepChainOfOpenAsyncScopesDoesNotBreakTheOriginsCapture() throws Exception {
    context.enterMethod(ORIGIN_CALL);
    var snapshot = context.snapshot();
    startWorkerHolding(snapshot);
    chainBuilt.await();

    // The origin thread does nothing exotic: it asks for its own request's trace.
    var tree = context.captureTrace();

    assertThat(tree.roots()).as("the origin's own call is still in its own trace").isNotEmpty();
  }

  /**
   * A worker that activates a scope per unit of work and hands the handle to a registry instead of
   * closing it in a {@code try}-with-resources — the shape any framework that completes work out of
   * order is forced into. No sleeping: the worker signals when the chain stands and blocks until
   * the test releases it, so every scope is open for the whole of the capture.
   */
  private void startWorkerHolding(ContextSnapshot snapshot) {
    worker =
        new Thread(
            () -> {
              var registry = new ArrayList<ContextScope>(OPEN_SCOPES);
              registry.add(snapshot.activate());
              for (var i = 1; i < OPEN_SCOPES; i++) {
                registry.add(context.snapshot().activate());
              }
              chainBuilt.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              for (var i = registry.size() - 1; i >= 0; i--) {
                registry.get(i).close();
              }
            },
            "adv-2026-10-26-1-worker");
    worker.start();
  }

  /**
   * B-198 boundary, end to end: a chain one link past the cap is not lost, only delayed — the link
   * past the cap is invisible to a capture while open and handed over whole at scope close.
   */
  @Test
  @DisplayName("B-198: work past the live-child depth cap reaches the origin when its scope closes")
  void workPastTheDepthCapIsHandedOverAtClose() throws Exception {
    var links = ThreadLocalNarrativeContext.TraceStack.MAX_LIVE_CHILD_DEPTH + 1;
    var step = new MethodSignature("Worker", "step", List.of());
    context.enterMethod(ORIGIN_CALL);
    var snapshot = context.snapshot();
    var whileOpen = new java.util.concurrent.atomic.AtomicInteger(-1);
    var chainStands = new CountDownLatch(1);
    var captured = new CountDownLatch(1);
    var closed = new CountDownLatch(1);
    worker =
        new Thread(
            () -> {
              var registry = new ArrayList<ContextScope>();
              registry.add(snapshot.activate());
              for (var i = 0; i < links; i++) {
                context.enterMethod(step);
                context.exitMethodWithReturn(null);
                if (i < links - 1) {
                  registry.add(context.snapshot().activate());
                }
              }
              chainStands.countDown();
              try {
                captured.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              for (var i = registry.size() - 1; i >= 0; i--) {
                registry.get(i).close();
              }
              closed.countDown();
            },
            "b198-boundary-worker");
    worker.start();

    chainStands.await();
    whileOpen.set(stepCount());
    captured.countDown();
    closed.await();

    assertThat(whileOpen.get())
        .as("the link past the cap is not visible while open")
        .isEqualTo(links - 1);
    assertThat(stepCount()).as("and arrives whole at close").isEqualTo(links);
  }

  private int stepCount() {
    return (int)
        context.events().stream()
            .filter(e -> e instanceof ai.narrativetrace.api.event.TraceEvent.EnterEvent)
            .map(e -> (ai.narrativetrace.api.event.TraceEvent.EnterEvent) e)
            .filter(e -> "step".equals(e.signature().methodName()))
            .count();
  }
}
