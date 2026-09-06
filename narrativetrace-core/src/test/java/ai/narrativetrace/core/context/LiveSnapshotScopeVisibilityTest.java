/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Pins the async adoption race deterministically: a worker publishes its spans long before its
 * scope closes, and a framework can hand control back to the caller in between — Spring completes
 * an {@code @Async} {@code CompletableFuture} inside the decorated task, so {@code future.get()}
 * returns while the task decorator's scope is still open.
 *
 * <p>No sleeping and no waiting for the worker: two latches hold it at the exact instant between
 * "the call is published" and "the scope closes", which is the window the race lives in.
 */
class LiveSnapshotScopeVisibilityTest {

  private static final MethodSignature CALLER =
      new MethodSignature("OrderService", "placeOrder", List.of());
  private static final MethodSignature WORKER_CALL =
      new MethodSignature("NotificationService", "notifyOrderPlaced", List.of());

  @Test
  void theWorkersCallIsVisibleToTheOriginWhileTheScopeIsStillOpen() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ORD-1\"");

    var published = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker = workerHoldingItsScopeOpen(context, snapshot, published, release, true);
    worker.start();
    try {
      assertThat(published.await(5, SECONDS)).isTrue();

      var beforeClose = context.captureTrace();

      assertThat(methodNames(beforeClose))
          .as("the caller observed the worker's completion, so its trace must already show it")
          .containsExactly("placeOrder", "notifyOrderPlaced");
    } finally {
      release.countDown();
      worker.join(5_000);
    }

    var afterClose = context.captureTrace();

    assertThat(methodNames(afterClose))
        .as("scope close adopts the same spans — it must not add a second copy of them")
        .containsExactly("placeOrder", "notifyOrderPlaced");
  }

  @Test
  void theWorkersCallNestsUnderTheCallerWhileTheScopeIsStillOpen() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ORD-1\"");

    var published = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker = workerHoldingItsScopeOpen(context, snapshot, published, release, true);
    worker.start();
    try {
      assertThat(published.await(5, SECONDS)).isTrue();

      var tree = context.captureTrace();

      assertThat(tree.roots()).hasSize(1);
      var root = tree.roots().get(0);
      assertThat(root.signature().methodName()).isEqualTo("placeOrder");
      assertThat(root.children()).hasSize(1);
      assertThat(root.children().get(0).signature().methodName()).isEqualTo("notifyOrderPlaced");
    } finally {
      release.countDown();
      worker.join(5_000);
    }
  }

  @Test
  void aScopeActivatedWithoutAdoptionStaysOutOfTheOriginsTraceOpenOrClosed() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ORD-1\"");

    var published = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker = workerHoldingItsScopeOpen(context, snapshot, published, release, false);
    worker.start();
    try {
      assertThat(published.await(5, SECONDS)).isTrue();

      assertThat(methodNames(context.captureTrace()))
          .as("helpers that re-emit their own children must not have them counted twice")
          .containsExactly("placeOrder");
    } finally {
      release.countDown();
      worker.join(5_000);
    }

    assertThat(methodNames(context.captureTrace())).containsExactly("placeOrder");
  }

  @Test
  void closingTheScopeEndsTheLiveRegistration() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    var origin = currentStackOf(context);
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ORD-1\"");

    runWorkerToCompletion(context, snapshot);

    assertThat(origin.liveChildSpanIds())
        .as("a finished worker's stack must not stay pinned to the origin for its whole life")
        .isEmpty();
    assertThat(methodNames(context.captureTrace()))
        .as("adoption has taken the same spans over, so nothing is lost by unregistering")
        .containsExactly("placeOrder", "notifyOrderPlaced");
  }

  @Test
  void theWorkersCallSurvivesTheCollectionOfItsStack() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    var snapshot = context.snapshot();
    context.exitMethodWithReturn("\"ORD-1\"");
    runWorkerToCompletion(context, snapshot);

    // The registry holds the child weakly, so only adoption makes the visibility permanent.
    System.gc(); // NOPMD - intentional GC for WeakReference test

    assertThat(methodNames(context.captureTrace()))
        .containsExactly("placeOrder", "notifyOrderPlaced");
  }

  @Test
  void aSnapshotWhoseOriginIsGoneIsSilentAtActivationAndAtClose() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(CALLER);
    context.exitMethodWithReturn("\"ORD-1\"");
    var snapshot = context.snapshot();
    context.reset();
    System.gc(); // NOPMD - intentional GC for WeakReference test

    // Registration must skip a collected origin exactly as adoption does — an orphaned worker
    // must neither fail nor resurrect the stack it was launched from.
    runWorkerToCompletion(context, snapshot);

    assertThat(context.captureTrace().roots()).isEmpty();
  }

  private static TraceStack currentStackOf(ThreadLocalNarrativeContext context) {
    var stack = context.swapStack(new TraceStack());
    context.swapStack(stack);
    return stack;
  }

  /** Runs one traced call under the snapshot on another thread and closes the scope. */
  private static void runWorkerToCompletion(
      ThreadLocalNarrativeContext context, ContextSnapshot snapshot) throws Exception {
    var worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "async-notify-1"));
    try {
      worker
          .submit(
              () -> {
                try (var scope = snapshot.activate()) {
                  context.enterMethod(WORKER_CALL);
                  context.exitMethodWithReturn("true");
                }
              })
          .get(5, SECONDS);
    } finally {
      worker.shutdownNow();
    }
  }

  /**
   * A worker that traces one call, announces it, and then blocks <em>before</em> closing its scope
   * — the state a Spring {@code @Async} worker is in when the caller's {@code future.get()}
   * returns.
   */
  private static Thread workerHoldingItsScopeOpen(
      ThreadLocalNarrativeContext context,
      ContextSnapshot snapshot,
      CountDownLatch published,
      CountDownLatch release,
      boolean adopt) {
    return new Thread(
        () -> {
          try (var scope = adopt ? snapshot.activate() : snapshot.activateWithoutAdoption()) {
            context.enterMethod(WORKER_CALL);
            context.exitMethodWithReturn("true");
            published.countDown();
            if (!release.await(5, SECONDS)) {
              throw new IllegalStateException("the test never released the worker");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        },
        "async-notify-1");
  }

  private static List<String> methodNames(TraceTree tree) {
    var names = new ArrayList<String>();
    collectMethodNames(tree.roots(), names);
    return names;
  }

  private static void collectMethodNames(List<TraceNode> nodes, List<String> names) {
    for (TraceNode node : nodes) {
      names.add(node.signature().methodName());
      collectMethodNames(node.children(), names);
    }
  }
}
