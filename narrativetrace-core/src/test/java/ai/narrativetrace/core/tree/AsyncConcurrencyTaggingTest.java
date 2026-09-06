/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Work adopted from a propagated snapshot is concurrent with the caller, and the structural
 * artifact must say so — and must not assert an order the scheduler chose. Same convention forks
 * have always used: a marker, members sorted by signature.
 */
class AsyncConcurrencyTaggingTest {

  private ThreadLocalNarrativeContext context;
  private ExecutorService workers;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    workers = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    workers.shutdownNow();
    workers.awaitTermination(5, TimeUnit.SECONDS);
    context.reset();
  }

  @Test
  void anAsyncChildIsTaggedAsConcurrent() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    runUnderSnapshot("Notifier", "notifyAsync");
    context.exitMethodWithReturn("ok");

    var child = context.captureTrace().roots().get(0).children().get(0);

    assertThat(child.concurrency()).isNotNull();
    assertThat(child.concurrency().kind()).isEqualTo(ConcurrencyKind.ASYNC);
    assertThat(child.concurrency().groupId()).startsWith("async-");
  }

  @Test
  void asyncChildrenOfTheSameParentShareAGroup() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    runUnderSnapshot("Notifier", "notifyAsync");
    runUnderSnapshot("Auditor", "record");
    context.exitMethodWithReturn("ok");

    var children = context.captureTrace().roots().get(0).children();

    assertThat(children).hasSize(2);
    assertThat(children.get(0).concurrency().groupId())
        .isEqualTo(children.get(1).concurrency().groupId());
  }

  @Test
  void nestedCallsInsideTheAsyncTaskAreNotThemselvesTagged() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    var snapshot = context.snapshot();
    workers
        .submit(
            () -> {
              try (var scope = snapshot.activate()) {
                context.enterMethod(sig("Notifier", "notifyAsync"));
                context.enterMethod(sig("EmailGateway", "send"));
                context.exitMethodWithReturn("true");
                context.exitMethodWithReturn("true");
              }
            })
        .get(5, TimeUnit.SECONDS);
    context.exitMethodWithReturn("ok");

    var async = context.captureTrace().roots().get(0).children().get(0);

    assertThat(async.concurrency().kind()).isEqualTo(ConcurrencyKind.ASYNC);
    assertThat(async.children().get(0).concurrency()).isNull();
  }

  @Test
  void ordinarySynchronousCallsAreNotTagged() {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.enterMethod(sig("Inventory", "reserve"));
    context.exitMethodWithReturn("ok");
    context.exitMethodWithReturn("ok");

    var child = context.captureTrace().roots().get(0).children().get(0);

    assertThat(child.concurrency()).isNull();
  }

  @Test
  void theStructuralArtifactMarksAndSortsAnAsyncGroup() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    runUnderSnapshot("Zebra", "last");
    runUnderSnapshot("Alpha", "first");
    context.exitMethodWithReturn("ok");

    var document =
        new StructuralTraceRenderer().renderDocument(context.captureTrace(), "order placed");

    assertThat(document).contains("~ async [2]");
    assertThat(document.indexOf("Alpha.first")).isLessThan(document.indexOf("Zebra.last"));
  }

  @Test
  void asyncRootsAreMarkedAndSortedToo() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("ok");
    runUnderSnapshot("Zebra", "last");
    runUnderSnapshot("Alpha", "first");

    var document =
        new StructuralTraceRenderer().renderDocument(context.captureTrace(), "order placed");

    assertThat(document).contains("~ async [2]");
    assertThat(document.indexOf("Alpha.first")).isLessThan(document.indexOf("Zebra.last"));
  }

  @RepeatedTest(8)
  void theArtifactIsByteIdenticalHoweverTheSchedulerDispatches() throws Exception {
    context.enterMethod(sig("OrderService", "placeOrder"));
    var snapshot = context.snapshot();
    var tasks =
        List.of(
            asyncTask(snapshot, "Charlie", "third"),
            asyncTask(snapshot, "Alpha", "first"),
            asyncTask(snapshot, "Bravo", "second"),
            asyncTask(snapshot, "Delta", "fourth"));
    for (var future : workers.invokeAll(tasks)) {
      future.get(5, TimeUnit.SECONDS);
    }
    context.exitMethodWithReturn("ok");

    var document =
        new StructuralTraceRenderer().renderDocument(context.captureTrace(), "order placed");

    assertThat(document)
        .isEqualTo(
            """
            scenario: order placed

            - OrderService.placeOrder() → value
              ~ async [4]
                - Alpha.first() → value
                - Bravo.second() → value
                - Charlie.third() → value
                - Delta.fourth() → value
            """);
  }

  private java.util.concurrent.Callable<Void> asyncTask(
      ai.narrativetrace.core.context.ContextSnapshot snapshot, String className, String method) {
    return () -> {
      try (var scope = snapshot.activate()) {
        context.enterMethod(sig(className, method));
        context.exitMethodWithReturn("true");
      }
      return null;
    };
  }

  private void runUnderSnapshot(String className, String method) throws Exception {
    var snapshot = context.snapshot();
    workers.submit(asyncTask(snapshot, className, method)).get(5, TimeUnit.SECONDS);
  }

  private static MethodSignature sig(String className, String methodName) {
    return new MethodSignature(className, methodName, List.of());
  }
}
