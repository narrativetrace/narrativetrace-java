/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ForkGroupTest {

  @Test
  void createGeneratesUniqueGroupIds() {
    var context = new ThreadLocalNarrativeContext();
    var group1 = ForkGroup.create(context);
    var group2 = ForkGroup.create(context);

    assertThat(group1.groupId()).isNotEqualTo(group2.groupId());
  }

  @Test
  void wrapSupplierExecutesTaskAndReturnsResult() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task = () -> "hello";
    var wrapped = group.wrap(task);
    var result = wrapped.get();

    assertThat(result).isEqualTo("hello");
  }

  @Test
  void wrapSupplierCollectsChildTraceRoots() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("ChildSvc", "compute", List.of()));
          context.exitMethodWithReturn("42");
          return "42";
        };
    group.wrap(task).get();
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("ChildSvc");
  }

  @Test
  void wrapRunnableCollectsChildTraceRoots() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Runnable task =
        () -> {
          context.enterMethod(new MethodSignature("Worker", "process", List.of()));
          context.exitMethodWithReturn(null);
        };
    group.wrap(task).run();
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("Worker");
  }

  @Test
  void wrapCallableCollectsChildTraceRoots() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Callable<Integer> callable =
        () -> {
          context.enterMethod(new MethodSignature("CalcSvc", "sum", List.of()));
          context.exitMethodWithReturn("100");
          return 100;
        };
    group.wrap(callable).call();
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("CalcSvc");
  }

  @Test
  void mergeGraftsCollectedRootsIntoParentFrame() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    var group = ForkGroup.create(context);
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("ChildSvc", "compute", List.of()));
          context.exitMethodWithReturn("42");
          return "42";
        };
    group.wrap(task).get();
    group.merge();

    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.signature().className()).isEqualTo("Parent");
    assertThat(parent.children()).hasSize(1);
    assertThat(parent.children().get(0).signature().className()).isEqualTo("ChildSvc");
  }

  @Test
  void mergedNodesCarryCorrectGroupId() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };
    group.wrap(task).get();
    group.merge();

    var root = context.captureTrace().roots().get(0);
    assertThat(root.concurrency()).isNotNull();
    assertThat(root.concurrency().groupId()).isEqualTo(group.groupId());
    assertThat(root.concurrency().kind()).isEqualTo(ConcurrencyKind.FORK_JOIN);
  }

  @Test
  void mergedNodesCarryCorrectThreadName() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    String expectedThreadName = Thread.currentThread().getName();
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };
    group.wrap(task).get();
    group.merge();

    var root = context.captureTrace().roots().get(0);
    assertThat(root.concurrency().threadName()).isEqualTo(expectedThreadName);
  }

  @Test
  void mergedNodesCarryCorrectThreadId() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    long expectedThreadId = Thread.currentThread().getId();
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };
    group.wrap(task).get();
    group.merge();

    var root = context.captureTrace().roots().get(0);
    assertThat(root.concurrency().threadId()).isEqualTo(expectedThreadId);
  }

  @Test
  void mergedNodesFromVirtualThreadCarryVirtualTrue() throws Exception {
    try {
      Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
    } catch (NoSuchMethodException e) {
      // Java < 21 — virtual threads not available, skip test
      return;
    }

    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };

    ExecutorService executor =
        (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
    try {
      CompletableFuture.supplyAsync(group.wrap(task), executor).join();
    } finally {
      executor.shutdown();
    }
    group.merge();

    var root = context.captureTrace().roots().get(0);
    assertThat(root.concurrency().virtual()).isTrue();
  }

  @Test
  void mergedNodesFromPlatformThreadCarryVirtualFalse() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };
    group.wrap(task).get();
    group.merge();

    var root = context.captureTrace().roots().get(0);
    assertThat(root.concurrency().virtual()).isFalse();
  }

  @Test
  void twoTasksOnDifferentThreadsProduceCorrectTree() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    var group = ForkGroup.create(context);
    runForkedPair(context, group, "SvcA", "compute", "SvcB", "lookup");
    group.merge();
    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.children()).hasSize(2);
    assertThat(parent.children())
        .extracting(n -> n.signature().className())
        .containsExactlyInAnyOrder("SvcA", "SvcB");
    assertThat(parent.children())
        .allSatisfy(n -> assertThat(n.concurrency().groupId()).isEqualTo(group.groupId()));
  }

  @Test
  void mergedChildrenAppearAsSiblingsOfSequentialChildren() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "run", List.of()));

    // Sequential child first
    context.enterMethod(new MethodSignature("SeqSvc", "prepare", List.of()));
    context.exitMethodWithReturn("ready");

    // Then fork-join
    var group = ForkGroup.create(context);
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("AsyncSvc", "compute", List.of()));
          context.exitMethodWithReturn("result");
          return "result";
        };
    group.wrap(task).get();
    group.merge();

    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.children()).hasSize(2);
    assertThat(parent.children().get(0).signature().className()).isEqualTo("SeqSvc");
    assertThat(parent.children().get(0).concurrency()).isNull();
    assertThat(parent.children().get(1).signature().className()).isEqualTo("AsyncSvc");
    assertThat(parent.children().get(1).concurrency()).isNotNull();
  }

  @Test
  void mergeOnInactiveContextIsNoOp() {
    var context = NoopNarrativeContext.INSTANCE;
    var group = ForkGroup.create(context);

    // wrap + merge should not throw even though context discards everything
    Supplier<String> task = () -> "ok";
    group.wrap(task).get();
    group.merge();

    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void wrapPreservesExceptionPropagation() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          throw new IllegalStateException("boom");
        };
    var wrapped = group.wrap(task);

    assertThatThrownBy(wrapped::get).isInstanceOf(IllegalStateException.class).hasMessage("boom");
  }

  @Test
  void wrapSupplierCollectsChildTraceRootsOnException() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("FailSvc", "crash", List.of()));
          var ex = new IllegalStateException("boom");
          context.exitMethodWithException(ex, null);
          throw ex;
        };
    try {
      group.wrap(task).get();
    } catch (IllegalStateException ignored) {
      // expected — exception propagation tested separately
    }
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("FailSvc");
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void wrapRunnableCollectsChildTraceRootsOnException() {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Runnable task =
        () -> {
          context.enterMethod(new MethodSignature("FailSvc", "crash", List.of()));
          var ex = new IllegalStateException("boom");
          context.exitMethodWithException(ex, null);
          throw ex;
        };
    try {
      group.wrap(task).run();
    } catch (IllegalStateException ignored) {
      // expected
    }
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("FailSvc");
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void wrapCallableCollectsChildTraceRootsOnException() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var group = ForkGroup.create(context);

    Callable<String> task =
        () -> {
          context.enterMethod(new MethodSignature("FailSvc", "crash", List.of()));
          var ex = new IllegalStateException("boom");
          context.exitMethodWithException(ex, null);
          throw ex;
        };
    try {
      group.wrap(task).call();
    } catch (IllegalStateException ignored) {
      // expected
    }
    group.merge();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("FailSvc");
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void mergeWithNoTasksIsNoOp() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "run", List.of()));

    var group = ForkGroup.create(context);
    group.merge(); // nothing was wrapped

    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.children()).isEmpty();
  }

  @Test
  void repeatedMergeDoesNotDuplicateGraftedNodes() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "run", List.of()));

    var group = ForkGroup.create(context);
    wrapAndRun(context, group, "Svc", "work");
    group.merge();
    group.merge(); // second merge should be a no-op

    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.children()).hasSize(1);
  }

  @Test
  void twoForkGroupsHaveDistinctGroupIdsAndSeparateChildren() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "run", List.of()));

    var group1 = ForkGroup.create(context);
    wrapAndRun(context, group1, "SvcA", "work");
    group1.merge();

    var group2 = ForkGroup.create(context);
    wrapAndRun(context, group2, "SvcB", "work");
    group2.merge();

    context.exitMethodWithReturn("done");

    assertThat(group1.groupId()).isNotEqualTo(group2.groupId());
    var parent = context.captureTrace().roots().get(0);
    assertThat(parent.children()).hasSize(2);
    assertThat(parent.children().get(0).concurrency().groupId()).isEqualTo(group1.groupId());
    assertThat(parent.children().get(1).concurrency().groupId()).isEqualTo(group2.groupId());
  }

  @Test
  void forkGroupChildrenShareTraceId() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    var group = ForkGroup.create(context);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var f1 =
          CompletableFuture.supplyAsync(group.wrap(tracingTask(context, "A", "work")), executor);
      var f2 =
          CompletableFuture.supplyAsync(group.wrap(tracingTask(context, "B", "work")), executor);
      CompletableFuture.allOf(f1, f2).join();
    } finally {
      executor.shutdown();
    }
    group.merge();
    context.exitMethodWithReturn("ok");

    var root = context.captureTrace().roots().get(0);
    var rootTraceId = root.spanContext().traceId();
    for (var child : root.children()) {
      assertThat(child.spanContext().traceId()).isEqualTo(rootTraceId);
    }
  }

  @Test
  void forkGroupChildRootsCorrectParentSpanId() {
    var context = new ThreadLocalNarrativeContext();
    SpanId parentSpanId = context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    var group = ForkGroup.create(context);
    wrapAndRun(context, group, "A", "work");
    group.merge();
    context.exitMethodWithReturn("ok");

    var root = context.captureTrace().roots().get(0);
    var child = root.children().get(0);
    assertThat(child.spanContext().parentSpanId()).isEqualTo(parentSpanId);
  }

  @Test
  void forkGroupMergePreservesSpanContext() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    var group = ForkGroup.create(context);
    wrapAndRun(context, group, "Worker", "process");
    group.merge();
    context.exitMethodWithReturn("ok");

    var root = context.captureTrace().roots().get(0);
    var child = root.children().get(0);
    assertThat(child.spanContext()).isNotNull();
    assertThat(child.spanContext().spanId().value()).matches("[0-9a-f]{16}");
    assertThat(child.spanContext().traceId().value()).matches("[0-9a-f]{32}");
  }

  @Test
  void deferredFutureExitMatchesSpanId() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    // Enter, detach, and complete via deferred exit on another thread
    SpanId spanId = context.enterMethod(new MethodSignature("Svc", "asyncOp", List.of()));
    context.detachFrame(spanId);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(() -> context.exitMethodWithReturn("\"resolved\"", spanId)).get();
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.children()).hasSize(1);
    var child = root.children().get(0);
    assertThat(child.signature().methodName()).isEqualTo("asyncOp");
    assertThat(child.spanContext().spanId()).isEqualTo(spanId);
  }

  private Supplier<String> tracingTask(NarrativeContext context, String className, String method) {
    return () -> {
      context.enterMethod(new MethodSignature(className, method, List.of()));
      context.exitMethodWithReturn("ok");
      return "ok";
    };
  }

  private void wrapAndRun(NarrativeContext context, ForkGroup group, String cls, String method) {
    group.wrap(tracingTask(context, cls, method)).get();
  }

  @Test
  void forkGroupCreationFiresLifecycleCallback() {
    var firedGroupIds = new ArrayList<String>();
    var context = new LifecycleSpyContext(new ThreadLocalNarrativeContext());
    context.onForkCreatedCallback = firedGroupIds::add;

    var group = ForkGroup.create(context);

    assertThat(firedGroupIds).containsExactly(group.groupId());
  }

  @Test
  void forkGroupMergeFiresLifecycleCallbackWithMemberCount() {
    var mergedGroupIds = new ArrayList<String>();
    var delegate = new ThreadLocalNarrativeContext();
    var context = new LifecycleSpyContext(delegate);
    context.onMergeCallback = mergedGroupIds::add;

    var group = ForkGroup.create(context);
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("Svc", "work", List.of()));
          context.exitMethodWithReturn("ok");
          return "ok";
        };
    group.wrap(task).get();
    group.merge();

    assertThat(mergedGroupIds).containsExactly(group.groupId());
  }

  private void runForkedPair(
      NarrativeContext context,
      ForkGroup group,
      String clsA,
      String methodA,
      String clsB,
      String methodB) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var a =
          CompletableFuture.supplyAsync(group.wrap(tracingTask(context, clsA, methodA)), executor);
      var b =
          CompletableFuture.supplyAsync(group.wrap(tracingTask(context, clsB, methodB)), executor);
      CompletableFuture.allOf(a, b).join();
    } finally {
      executor.shutdown();
    }
  }

  static class LifecycleSpyContext implements NarrativeContext {
    private final NarrativeContext delegate;
    Consumer<String> onForkCreatedCallback = groupId -> {};
    Consumer<String> onMergeCallback = groupId -> {};

    LifecycleSpyContext(NarrativeContext delegate) {
      this.delegate = delegate;
    }

    @Override
    public SpanId enterMethod(MethodSignature sig) {
      return delegate.enterMethod(sig);
    }

    @Override
    public void detachFrame(SpanId spanId) {
      delegate.detachFrame(spanId);
    }

    @Override
    public void exitMethodWithReturn(String val) {
      delegate.exitMethodWithReturn(val);
    }

    @Override
    public void exitMethodWithReturn(String val, SpanId spanId) {
      delegate.exitMethodWithReturn(val, spanId);
    }

    @Override
    public void exitMethodWithException(Throwable ex, String ctx) {
      delegate.exitMethodWithException(ex, ctx);
    }

    @Override
    public void exitMethodWithException(Throwable ex, String ctx, SpanId spanId) {
      delegate.exitMethodWithException(ex, ctx, spanId);
    }

    @Override
    public TraceTree captureTrace() {
      return delegate.captureTrace();
    }

    @Override
    public TraceTree captureLocalTrace() {
      return delegate.captureLocalTrace();
    }

    @Override
    public void reset() {
      delegate.reset();
    }

    @Override
    public SpanId currentSpanId() {
      return delegate.currentSpanId();
    }

    @Override
    public void emitTraceNode(TraceNode node, SpanId parentSpanId) {
      delegate.emitTraceNode(node, parentSpanId);
    }

    @Override
    public ContextSnapshot snapshot() {
      return delegate.snapshot();
    }

    @Override
    public void onForkCreated(String groupId) {
      onForkCreatedCallback.accept(groupId);
    }

    @Override
    public void onMerge(String groupId, List<TraceNode> members) {
      onMergeCallback.accept(groupId);
    }
  }
}
