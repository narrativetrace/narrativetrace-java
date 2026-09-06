/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ServiceIdentity;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class NarrativeTraceThreadLocalAccessorTest {

  @Test
  void hasKeyAndCanRegister() {
    var accessor = new NarrativeTraceThreadLocalAccessor();
    assertThat(accessor.key()).isEqualTo("narrativetrace");

    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);
  }

  @Test
  void getValueReturnsSnapshot() {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    ContextSnapshot snapshot = accessor.getValue();
    assertThat(snapshot).isNotNull();
  }

  @Test
  void setValueActivatesSnapshotAndResetRestores() {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    // Trace something on this thread
    context.enterMethod(new MethodSignature("Original", "method", List.of()));
    context.exitMethodWithReturn("orig");
    assertThat(context.captureTrace().roots()).hasSize(1);

    // setValue activates a fresh scope
    var snapshot = accessor.getValue();
    accessor.setValue(snapshot);

    // Fresh scope — empty trace
    context.enterMethod(new MethodSignature("Scoped", "work", List.of()));
    context.exitMethodWithReturn("scoped");
    assertThat(context.captureTrace().roots()).hasSize(1);
    assertThat(context.captureTrace().roots().get(0).signature().className()).isEqualTo("Scoped");

    // setValue() (no-arg) restores previous state — and the work done inside the scope joins the
    // trace the snapshot was taken from, exactly as async work does when it lands on a worker.
    accessor.setValue();

    var restored = context.captureTrace();
    assertThat(restored.roots()).hasSize(2);
    assertThat(restored.roots().get(0).signature().className()).isEqualTo("Original");
    assertThat(restored.roots().get(1).signature().className()).isEqualTo("Scoped");
  }

  @Test
  void nestedSetValueClosePreviousScope() {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    context.enterMethod(new MethodSignature("Original", "method", List.of()));
    context.exitMethodWithReturn("orig");

    var snapshot1 = accessor.getValue();
    accessor.setValue(snapshot1);

    // Second setValue without closing first — must not leak
    var snapshot2 = accessor.getValue();
    accessor.setValue(snapshot2);

    // Reset should close cleanly
    accessor.setValue();

    // The original trace should be accessible (not corrupted by leak)
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("Original");
  }

  @Test
  void setValueNoArgWithoutPriorSetValueIsNoOp() {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    // Should not throw when no scope was previously set
    accessor.setValue();

    // Context still works
    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.exitMethodWithReturn("ok");
    assertThat(context.captureTrace().roots()).hasSize(1);
  }

  @Test
  void endToEndWithContextSnapshotFactory() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);

    var factory = ContextSnapshotFactory.builder().contextRegistry(registry).build();

    // Capture snapshot on main thread
    var micrometerSnapshot = factory.captureAll();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = micrometerSnapshot.setThreadLocals()) {
                      context.enterMethod(
                          new MethodSignature("ChildService", "process", List.of()));
                      context.exitMethodWithReturn("done");
                      return context.captureTrace();
                    }
                  })
              .get();

      assertThat(childTree.roots()).hasSize(1);
      assertThat(childTree.roots().get(0).signature().className()).isEqualTo("ChildService");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void accessorPropagatesTraceId() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);
    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);
    var factory = ContextSnapshotFactory.builder().contextRegistry(registry).build();

    context.enterMethod(new MethodSignature("Root", "call", List.of()));
    var parentTraceId = context.captureTrace().roots().get(0).spanContext().traceId();
    var micrometerSnapshot = factory.captureAll();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childTraceId =
          executor
              .submit(
                  () -> {
                    try (var scope = micrometerSnapshot.setThreadLocals()) {
                      context.enterMethod(new MethodSignature("Child", "work", List.of()));
                      context.exitMethodWithReturn("ok");
                      return context.captureTrace().roots().get(0).spanContext().traceId();
                    }
                  })
              .get();

      assertThat(childTraceId).isEqualTo(parentTraceId);
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void accessorPropagatesServiceIdentity() throws Exception {
    var identity = new ServiceIdentity("order-svc", "1.0.0", "prod");
    var context =
        new ThreadLocalNarrativeContext(
            new NarrativeTraceConfig(), new DualPathPipeline(), identity);
    var micrometerSnapshot = snapshotViaFactory(context);

    context.enterMethod(new MethodSignature("Root", "call", List.of()));
    var captured = micrometerSnapshot.captureAll();

    var childSc = runOnChildThread(context, captured);
    assertThat(childSc.serviceName()).isEqualTo("order-svc");
    assertThat(childSc.serviceVersion()).isEqualTo("1.0.0");
    assertThat(childSc.environment()).isEqualTo("prod");
    context.exitMethodWithReturn("ok");
  }

  @Test
  void captureAllShouldPreserveScopedParentWhenNoActiveFrameIsOnTheStack() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);
    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);
    var factory = ContextSnapshotFactory.builder().contextRegistry(registry).build();

    var parentSpanId = context.enterMethod(new MethodSignature("RootService", "begin", List.of()));
    context.detachFrame(parentSpanId);
    var previousScope = context.beginScope(parentSpanId);
    try {
      var micrometerSnapshot = factory.captureAll();
      var propagated = resolveParentOnChildThread(context, micrometerSnapshot);
      assertThat(propagated).isEqualTo(parentSpanId);
    } finally {
      context.endScope(previousScope);
    }
  }

  private static ai.narrativetrace.api.event.SpanId resolveParentOnChildThread(
      ThreadLocalNarrativeContext context, io.micrometer.context.ContextSnapshot snapshot)
      throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      return executor
          .submit(
              () -> {
                try (var scope = snapshot.setThreadLocals()) {
                  context.enterMethod(new MethodSignature("ChildService", "work", List.of()));
                  context.exitMethodWithReturn("\"ok\"");
                  return context.captureTrace().roots().get(0).spanContext().parentSpanId();
                }
              })
          .get();
    } finally {
      executor.shutdown();
    }
  }

  private static ContextSnapshotFactory snapshotViaFactory(ThreadLocalNarrativeContext context) {
    var accessor = new NarrativeTraceThreadLocalAccessor(context);
    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);
    return ContextSnapshotFactory.builder().contextRegistry(registry).build();
  }

  private static ai.narrativetrace.api.event.SpanContext runOnChildThread(
      ThreadLocalNarrativeContext context, io.micrometer.context.ContextSnapshot micrometerSnapshot)
      throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      return executor
          .submit(
              () -> {
                try (var scope = micrometerSnapshot.setThreadLocals()) {
                  context.enterMethod(new MethodSignature("Child", "work", List.of()));
                  context.exitMethodWithReturn("ok");
                  return context.captureTrace().roots().get(0).spanContext();
                }
              })
          .get();
    } finally {
      executor.shutdown();
    }
  }
}
