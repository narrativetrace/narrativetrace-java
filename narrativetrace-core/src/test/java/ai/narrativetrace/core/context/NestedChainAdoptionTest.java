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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the nested chain: origin → worker → grandchild, where the worker itself activates a snapshot
 * of its own stack.
 *
 * <p>INTENT: Adoption and live visibility used to hand over a child's <em>own</em> spans only, so a
 * grandchild's call reached the worker and stopped there. The origin — the thread whose request
 * this all is, and the only one anyone calls {@code captureTrace()} on — never saw it. A chain of
 * two async hops is not exotic: a controller dispatches to a service that dispatches to a client.
 *
 * <p><b>@llmNote</b> Three latches, no sleeping. The grandchild publishes and blocks before closing
 * its scope; the worker blocks before closing its own. That holds the chain at each of the three
 * instants that matter — all scopes open, the grandchild closed, everything closed — and the same
 * tree is asserted at all three, so a span that appears and then vanishes (or is counted twice)
 * fails.
 */
class NestedChainAdoptionTest {

  private static final MethodSignature ORIGIN_CALL =
      new MethodSignature("OrderService", "placeOrder", List.of());
  private static final MethodSignature WORKER_CALL =
      new MethodSignature("NotificationService", "notifyOrderPlaced", List.of());
  private static final MethodSignature GRANDCHILD_CALL =
      new MethodSignature("EmailGateway", "send", List.of());

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();
  private final Chain chain = new Chain();

  @AfterEach
  void releaseTheChain() throws Exception {
    chain.releaseEverything();
    context.reset();
  }

  @Test
  void theGrandchildsCallIsVisibleToTheOriginWhileEveryScopeIsStillOpen() throws Exception {
    chain.startFrom(context);

    assertThat(methodNames(context.captureTrace()))
        .as("two async hops from the origin is still the origin's request")
        .containsExactly("placeOrder", "notifyOrderPlaced", "send");
  }

  @Test
  void theGrandchildsCallStaysVisibleOnceItsOwnScopeCloses() throws Exception {
    chain.startFrom(context);

    chain.closeGrandchild();

    assertThat(methodNames(context.captureTrace()))
        .as("the worker adopted it; the origin must see it through the worker either way")
        .containsExactly("placeOrder", "notifyOrderPlaced", "send");
  }

  @Test
  void theGrandchildsCallStaysVisibleOnceEveryScopeCloses() throws Exception {
    chain.startFrom(context);

    chain.closeGrandchild();
    chain.closeWorker();

    assertThat(methodNames(context.captureTrace()))
        .as("hand-over must carry what the child adopted, not only what it created")
        .containsExactly("placeOrder", "notifyOrderPlaced", "send");
  }

  @Test
  void theWorkerClosingBeforeItsGrandchildStillHandsTheGrandchildOver() throws Exception {
    chain.startFrom(context);

    // The order a framework actually produces when the outer task returns first: the worker's
    // scope closes while its own child is still running.
    chain.closeWorker();

    assertThat(methodNames(context.captureTrace()))
        .containsExactly("placeOrder", "notifyOrderPlaced", "send");

    chain.closeGrandchild();

    assertThat(methodNames(context.captureTrace()))
        .as("nothing may appear twice once the late grandchild finishes")
        .containsExactly("placeOrder", "notifyOrderPlaced", "send");
  }

  @Test
  void theCallsNestOriginToWorkerToGrandchild() throws Exception {
    chain.startFrom(context);
    chain.closeGrandchild();
    chain.closeWorker();

    var tree = context.captureTrace();

    assertThat(tree.roots()).hasSize(1);
    var origin = tree.roots().get(0);
    assertThat(origin.signature().methodName()).isEqualTo("placeOrder");
    assertThat(origin.children()).hasSize(1);
    var worker = origin.children().get(0);
    assertThat(worker.signature().methodName()).isEqualTo("notifyOrderPlaced");
    assertThat(worker.children()).hasSize(1);
    assertThat(worker.children().get(0).signature().methodName()).isEqualTo("send");
  }

  @Test
  void everyCallInTheChainCarriesTheOriginsTraceId() throws Exception {
    chain.startFrom(context);
    chain.closeGrandchild();
    chain.closeWorker();

    var tree = context.captureTrace();

    var traceIds = new ArrayList<String>();
    collectTraceIds(tree.roots(), traceIds);
    assertThat(traceIds).hasSize(3).containsOnly(traceIds.get(0));
  }

  @Test
  void aGrandchildBehindAnUnadoptingWorkerReachesNeitherTheWorkerNorTheOrigin() throws Exception {
    chain.withWorkerAdoption(false).startFrom(context);

    assertThat(methodNames(context.captureTrace()))
        .as("the worker publishes its own children; its subtree must not be counted twice")
        .containsExactly("placeOrder");

    chain.closeGrandchild();
    chain.closeWorker();

    assertThat(methodNames(context.captureTrace())).containsExactly("placeOrder");
  }

  @Test
  void aGrandchildActivatedWithoutAdoptionStaysOutOfTheOriginsTrace() throws Exception {
    chain.withGrandchildAdoption(false).startFrom(context);

    assertThat(methodNames(context.captureTrace()))
        .containsExactly("placeOrder", "notifyOrderPlaced");

    chain.closeGrandchild();
    chain.closeWorker();

    assertThat(methodNames(context.captureTrace()))
        .as("what the child never joined, it cannot hand over")
        .containsExactly("placeOrder", "notifyOrderPlaced");
  }

  // ── the chain ───────────────────────────────────────────────────────────────

  /**
   * Two threads held open by latches: the worker activates a snapshot of the origin, the grandchild
   * a snapshot of the worker. Each blocks after publishing its call and closes only when released,
   * so the test controls which scopes are open at the moment it captures.
   */
  private static final class Chain {
    private final CountDownLatch grandchildPublished = new CountDownLatch(1);
    private final CountDownLatch releaseGrandchild = new CountDownLatch(1);
    private final CountDownLatch grandchildClosed = new CountDownLatch(1);
    private final CountDownLatch releaseWorker = new CountDownLatch(1);
    private final CountDownLatch workerClosed = new CountDownLatch(1);
    private boolean workerAdopts = true;
    private boolean grandchildAdopts = true;
    private Thread worker;
    private volatile Thread grandchild;

    Chain withWorkerAdoption(boolean adopts) {
      this.workerAdopts = adopts;
      return this;
    }

    Chain withGrandchildAdoption(boolean adopts) {
      this.grandchildAdopts = adopts;
      return this;
    }

    /**
     * Traces the origin call, launches the chain, and returns once the grandchild has published.
     */
    void startFrom(ThreadLocalNarrativeContext context) throws Exception {
      context.enterMethod(ORIGIN_CALL);
      var snapshot = context.snapshot();
      context.exitMethodWithReturn("\"ORD-1\"");

      worker = new Thread(() -> runWorker(context, snapshot), "async-notify-1");
      worker.start();
      if (!grandchildPublished.await(5, SECONDS)) {
        throw new IllegalStateException("the grandchild never published its call");
      }
    }

    /**
     * Deliberately does <em>not</em> join its grandchild before closing: a worker whose own scope
     * ends while its child is still running is the ordering a framework actually produces, and it
     * is the ordering that decides whether hand-over carries the whole chain.
     */
    private void runWorker(ThreadLocalNarrativeContext context, ContextSnapshot snapshot) {
      try (var scope = workerAdopts ? snapshot.activate() : snapshot.activateWithoutAdoption()) {
        context.enterMethod(WORKER_CALL);
        var nested = context.snapshot();
        context.exitMethodWithReturn("true");
        grandchild = new Thread(() -> runGrandchild(context, nested), "async-email-1");
        grandchild.start();
        await(releaseWorker);
      }
      workerClosed.countDown();
    }

    private void runGrandchild(ThreadLocalNarrativeContext context, ContextSnapshot snapshot) {
      try (var scope =
          grandchildAdopts ? snapshot.activate() : snapshot.activateWithoutAdoption()) {
        context.enterMethod(GRANDCHILD_CALL);
        context.exitMethodWithReturn("true");
        grandchildPublished.countDown();
        await(releaseGrandchild);
      }
      grandchildClosed.countDown();
    }

    void closeGrandchild() throws Exception {
      releaseGrandchild.countDown();
      if (!grandchildClosed.await(5, SECONDS)) {
        throw new IllegalStateException("the grandchild never closed its scope");
      }
    }

    void closeWorker() throws Exception {
      releaseWorker.countDown();
      if (!workerClosed.await(5, SECONDS)) {
        throw new IllegalStateException("the worker never closed its scope");
      }
    }

    void releaseEverything() throws Exception {
      releaseGrandchild.countDown();
      releaseWorker.countDown();
      if (worker != null) {
        worker.join(5_000);
      }
      if (grandchild != null) {
        grandchild.join(5_000);
      }
    }

    private static void await(CountDownLatch latch) {
      try {
        if (!latch.await(5, SECONDS)) {
          throw new IllegalStateException("the test never released a scope");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
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

  private static void collectTraceIds(List<TraceNode> nodes, List<String> ids) {
    for (TraceNode node : nodes) {
      if (node.spanContext() != null) {
        ids.add(node.spanContext().traceId().toString());
      }
      collectTraceIds(node.children(), ids);
    }
  }
}
