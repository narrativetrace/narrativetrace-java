/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FireAndForgetGroupTest {

  @Test
  void createInsertsLauncherNodeIntoParentFrame() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    FireAndForgetGroup.create(context, "Parent");

    context.exitMethodWithReturn("done");
    var parentNode = captureOnlyRoot(context);
    assertThat(parentNode.children()).hasSize(1);
    assertThat(parentNode.children().get(0).signature().methodName()).isEqualTo("fire-and-forget");
  }

  @Test
  void launcherNodeHasFireAndForgetKindAndGroupId() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    var group = FireAndForgetGroup.create(context, "Parent");

    context.exitMethodWithReturn("done");
    var launcher = captureOnlyRoot(context).children().get(0);
    assertThat(launcher.concurrency()).isNotNull();
    assertThat(launcher.concurrency().kind()).isEqualTo(ConcurrencyKind.FIRE_AND_FORGET);
    assertThat(launcher.concurrency().groupId()).isEqualTo(group.groupId());
  }

  @Test
  void launcherNodeHasEmptyChildrenAndNullOutcome() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    FireAndForgetGroup.create(context, "Parent");

    context.exitMethodWithReturn("done");
    var launcher = captureOnlyRoot(context).children().get(0);
    assertThat(launcher.children()).isEmpty();
    assertThat(launcher.outcome()).isNull();
  }

  @Test
  void wrapSupplierRunsTaskInSnapshotScope() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    var group = FireAndForgetGroup.create(context, "Parent");
    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("NotifySvc", "send", List.of()));
          context.exitMethodWithReturn("sent");
          return "sent";
        };
    var result = group.wrap(task).get();

    context.exitMethodWithReturn("done");
    var parentNode = captureOnlyRoot(context);
    assertThat(result).isEqualTo("sent");
    // Only the launcher node — child's traced method stayed in the snapshot scope
    assertThat(parentNode.children()).hasSize(1);
    assertThat(parentNode.children().get(0).signature().methodName()).isEqualTo("fire-and-forget");
  }

  @Test
  void wrapSupplierTagsChildRootsWithGroupId() {
    var context = new ThreadLocalNarrativeContext();
    var group = FireAndForgetGroup.create(context, "Parent");

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("NotifySvc", "send", List.of()));
          context.exitMethodWithReturn("sent");
          return "sent";
        };
    group.wrap(task).get();

    assertThat(group.childRoots()).hasSize(1);
    assertThat(group.childRoots().get(0).concurrency()).isNotNull();
    assertThat(group.childRoots().get(0).concurrency().groupId()).isEqualTo(group.groupId());
    assertThat(group.childRoots().get(0).concurrency().kind())
        .isEqualTo(ConcurrencyKind.FIRE_AND_FORGET);
  }

  @Test
  void childRootsPreserveOriginalSignatureWithMatchingGroupId() {
    var context = new ThreadLocalNarrativeContext();
    var group = FireAndForgetGroup.create(context, "Parent");

    Supplier<String> task =
        () -> {
          context.enterMethod(new MethodSignature("NotifySvc", "send", List.of()));
          context.exitMethodWithReturn("\"msg-42\"");
          return "msg-42";
        };
    group.wrap(task).get();

    var childRoot = group.childRoots().get(0);
    assertThat(childRoot.signature().className()).isEqualTo("NotifySvc");
    assertThat(childRoot.signature().methodName()).isEqualTo("send");
    assertThat(childRoot.outcome()).isNotNull();
    assertThat(childRoot.concurrency().groupId()).isEqualTo(group.groupId());
  }

  @Test
  void parentTreeUnaffectedByChildCompletionTiming() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));

    var group = FireAndForgetGroup.create(context, "Parent");
    Supplier<String> slowTask =
        () -> {
          context.enterMethod(new MethodSignature("SlowSvc", "process", List.of()));
          context.exitMethodWithReturn("done");
          return "done";
        };
    group.wrap(slowTask).get();

    context.exitMethodWithReturn("completed");

    var parent = captureOnlyRoot(context);
    assertThat(parent.children()).hasSize(1);
    assertThat(parent.children().get(0).signature().methodName()).isEqualTo("fire-and-forget");
    assertThat(parent.children().get(0).children()).isEmpty();
    assertThat(parent.outcome()).isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  void launcherNodeOnVirtualThreadCarriesVirtualTrue() throws Exception {
    try {
      Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
    } catch (NoSuchMethodException e) {
      // Java < 21 — virtual threads not available, skip test
      return;
    }

    var context = new ThreadLocalNarrativeContext();
    ExecutorService executor =
        (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
    try {
      // Create the group FROM a virtual thread so the launcher captures virtual=true.
      // captureTrace() is scoped to the calling thread's trace stack, so the capture
      // must also happen on the virtual thread that recorded the trace.
      var tree =
          CompletableFuture.supplyAsync(
                  () -> {
                    context.enterMethod(new MethodSignature("Parent", "orchestrate", List.of()));
                    FireAndForgetGroup.create(context, "Parent");
                    context.exitMethodWithReturn("done");
                    return context.captureTrace();
                  },
                  executor)
              .join();

      var parentNode = tree.roots().get(0);
      var launcher = parentNode.children().get(0);
      assertThat(launcher.concurrency().virtual()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void wrapSupplierCollectsChildRootsOnException() {
    var context = new ThreadLocalNarrativeContext();
    var group = FireAndForgetGroup.create(context, "Parent");

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
      // expected
    }

    assertThat(group.childRoots()).hasSize(1);
    assertThat(group.childRoots().get(0).signature().className()).isEqualTo("FailSvc");
    assertThat(group.childRoots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void fireAndForgetCreationFiresLifecycleCallback() {
    var firedGroupIds = new ArrayList<String>();
    var context = new LifecycleSpyContext(new ThreadLocalNarrativeContext());
    context.onFireAndForgetCallback = firedGroupIds::add;

    var group = FireAndForgetGroup.create(context, "Parent");

    assertThat(firedGroupIds).containsExactly(group.groupId());
  }

  @Test
  void fireAndForgetChildSharesTraceId() {
    var context = new ThreadLocalNarrativeContext();
    SpanId parentSpanId = context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    var group = FireAndForgetGroup.create(context, "Svc");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture.supplyAsync(
              group.wrap(
                  () -> {
                    context.enterMethod(new MethodSignature("Worker", "task", List.of()));
                    context.exitMethodWithReturn("ok");
                    return "ok";
                  }),
              executor)
          .join();
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");

    var root = context.captureTrace().roots().get(0);
    var rootTraceId = root.spanContext().traceId();
    for (var childRoot : group.childRoots()) {
      assertThat(childRoot.spanContext().traceId()).isEqualTo(rootTraceId);
    }
  }

  @Test
  void fireAndForgetParentSpanIdIsLauncher() {
    var context = new ThreadLocalNarrativeContext();
    SpanId parentSpanId = context.enterMethod(new MethodSignature("Svc", "root", List.of()));

    var group = FireAndForgetGroup.create(context, "Svc");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture.supplyAsync(
              group.wrap(
                  () -> {
                    context.enterMethod(new MethodSignature("Worker", "task", List.of()));
                    context.exitMethodWithReturn("ok");
                    return "ok";
                  }),
              executor)
          .join();
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");

    for (var childRoot : group.childRoots()) {
      assertThat(childRoot.spanContext().parentSpanId()).isEqualTo(parentSpanId);
    }
  }

  private TraceNode captureOnlyRoot(NarrativeContext context) {
    var roots = context.captureTrace().roots();
    assertThat(roots).hasSize(1);
    return roots.get(0);
  }

  static class LifecycleSpyContext implements NarrativeContext {
    private final NarrativeContext delegate;
    java.util.function.Consumer<String> onFireAndForgetCallback = groupId -> {};

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
    public void onFireAndForgetLaunched(String groupId) {
      onFireAndForgetCallback.accept(groupId);
    }
  }
}
