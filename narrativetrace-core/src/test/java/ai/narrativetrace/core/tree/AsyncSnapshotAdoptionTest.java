/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Work done on another thread under a propagated snapshot belongs to the snapshotting thread's
 * trace: the synchronous log stream already narrates it, so the captured tree must not silently
 * lose it.
 *
 * <p>Cross-package by design — everything here goes through the public context API.
 */
class AsyncSnapshotAdoptionTest {

  private ThreadLocalNarrativeContext context;
  private ExecutorService worker;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "async-worker"));
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    worker.shutdownNow();
    worker.awaitTermination(5, TimeUnit.SECONDS);
    context.reset();
  }

  @Test
  void asyncWorkStartedAfterTheParentReturnedBecomesASecondRoot() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("OrderResult");
    runUnderSnapshot(() -> traceCall("NotificationService", "notifyOrderPlaced"));

    var roots = context.captureTrace().roots();

    assertThat(names(roots))
        .containsExactly("OrderService.placeOrder", "NotificationService.notifyOrderPlaced");
  }

  @Test
  void asyncWorkStartedInsideTheParentBecomesAChild() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    runUnderSnapshot(() -> traceCall("NotificationService", "notifyOrderPlaced"));
    context.exitMethodWithReturn("OrderResult");

    var roots = context.captureTrace().roots();

    assertThat(names(roots)).containsExactly("OrderService.placeOrder");
    assertThat(names(roots.get(0).children()))
        .containsExactly("NotificationService.notifyOrderPlaced");
  }

  @Test
  void theAdoptedNodeReportsTheThreadThatRanIt() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("OrderResult");
    runUnderSnapshot(() -> traceCall("NotificationService", "notifyOrderPlaced"));

    var async = context.captureTrace().roots().get(1);

    assertThat(async.thread()).isNotNull();
    assertThat(async.thread().threadName()).isEqualTo("async-worker");
  }

  @Test
  void nestedAsyncCallsKeepTheirOwnShape() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    runUnderSnapshot(
        () -> {
          context.enterMethod(sig("NotificationService", "notifyOrderPlaced"));
          context.enterMethod(sig("EmailGateway", "send"));
          context.exitMethodWithReturn("true");
          context.exitMethodWithReturn("true");
        });
    context.exitMethodWithReturn("OrderResult");

    var root = context.captureTrace().roots().get(0);

    assertThat(names(root.children())).containsExactly("NotificationService.notifyOrderPlaced");
    assertThat(names(root.children().get(0).children())).containsExactly("EmailGateway.send");
  }

  @Test
  void workOnAThreadThatNeverActivatedTheSnapshotStaysOut() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("OrderResult");
    worker.submit(() -> traceCall("Unrelated", "backgroundSweep")).get(5, TimeUnit.SECONDS);

    var roots = context.captureTrace().roots();

    assertThat(names(roots)).containsExactly("OrderService.placeOrder");
  }

  @Test
  void forkGroupChildrenAreNotCountedTwice() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    var group = ForkGroup.create(context);
    var task = group.wrap((Runnable) () -> traceCall("InventoryService", "reserve"));
    CompletableFuture.runAsync(task, worker).get(5, TimeUnit.SECONDS);
    group.merge();
    context.exitMethodWithReturn("OrderResult");

    var root = context.captureTrace().roots().get(0);

    assertThat(names(root.children())).containsExactly("InventoryService.reserve");
  }

  @Test
  void adoptionDiesWithTheSnapshottingStack() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("OrderResult");
    var snapshot = context.snapshot();
    context.reset();

    worker
        .submit(
            () -> {
              try (var scope = snapshot.activate()) {
                traceCall("NotificationService", "notifyOrderPlaced");
              }
            })
        .get(5, TimeUnit.SECONDS);

    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void manyAsyncChildrenAllLandInTheTree() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    for (int i = 0; i < 50; i++) {
      String name = "notify" + i;
      runUnderSnapshot(() -> traceCall("NotificationService", name));
    }
    context.exitMethodWithReturn("OrderResult");

    var root = context.captureTrace().roots().get(0);

    assertThat(root.children()).hasSize(50);
  }

  private void runUnderSnapshot(Runnable body) throws Exception {
    var snapshot = context.snapshot();
    worker
        .submit(
            () -> {
              try (var scope = snapshot.activate()) {
                body.run();
              }
            })
        .get(5, TimeUnit.SECONDS);
  }

  private void traceCall(String className, String methodName) {
    context.enterMethod(sig(className, methodName));
    context.exitMethodWithReturn("true");
  }

  private static MethodSignature sig(String className, String methodName) {
    return new MethodSignature(className, methodName, List.of());
  }

  private static List<String> names(List<TraceNode> nodes) {
    return nodes.stream()
        .map(n -> n.signature().className() + "." + n.signature().methodName())
        .toList();
  }
}
