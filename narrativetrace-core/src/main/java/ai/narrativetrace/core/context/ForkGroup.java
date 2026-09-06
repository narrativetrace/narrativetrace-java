/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceNode;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Helper for fork-join style concurrency inside one trace.
 *
 * <p>INTENT: Use this when the parent launches concurrent tasks and later waits for them. Wrapped
 * tasks capture their work in isolated child scopes, and {@link #merge()} replays the collected
 * roots back under the parent span with {@link ConcurrencyInfo} tagged as {@link
 * ConcurrencyKind#FORK_JOIN}.
 *
 * <p><b>@llmNote</b> Creating a group is not enough. You must wrap every forked task and call
 * {@link #merge()} after the tasks complete, otherwise the collected roots stay detached from the
 * parent trace.
 *
 * <p><b>@sideEffects</b> A wrapped task ends by taking a copy of its own trace and discarding the
 * raw scope behind it. The copied {@link TraceNode}s are the record from then on: {@link #merge()}
 * re-emits them under the parent span, which creates the spans a reader sees. Keeping the raw ones
 * as well would leak them — a worker scope activated without adoption belongs to no thread's
 * reportable set, so no {@code reset()} could ever clear it.
 */
public final class ForkGroup {

  private static final AtomicLong ID_GENERATOR = new AtomicLong();

  private final NarrativeContext context;
  private final String groupId;
  private final SpanId parentSpanId;
  private final List<CollectedChild> children = new CopyOnWriteArrayList<>();

  private ForkGroup(NarrativeContext context, String groupId, SpanId parentSpanId) {
    this.context = context;
    this.groupId = groupId;
    this.parentSpanId = parentSpanId;
  }

  public static ForkGroup create(NarrativeContext context) {
    var group =
        new ForkGroup(context, "fork-" + ID_GENERATOR.incrementAndGet(), context.currentSpanId());
    context.onForkCreated(group.groupId);
    return group;
  }

  public String groupId() {
    return groupId;
  }

  public <T> Supplier<T> wrap(Supplier<T> task) {
    var snapshot = context.snapshot();
    return () -> {
      var thread = Thread.currentThread();
      try (var scope = snapshot.activateWithoutAdoption()) {
        try {
          return task.get();
        } finally {
          collectRoots(thread);
        }
      }
    };
  }

  public Runnable wrap(Runnable task) {
    var snapshot = context.snapshot();
    return () -> {
      var thread = Thread.currentThread();
      try (var scope = snapshot.activateWithoutAdoption()) {
        try {
          task.run();
        } finally {
          collectRoots(thread);
        }
      }
    };
  }

  public <T> Callable<T> wrap(Callable<T> task) {
    var snapshot = context.snapshot();
    return () -> {
      var thread = Thread.currentThread();
      try (var scope = snapshot.activateWithoutAdoption()) {
        try {
          return task.call();
        } finally {
          collectRoots(thread);
        }
      }
    };
  }

  public void merge() {
    var snapshot = List.copyOf(children);
    children.clear();
    var merged = new java.util.ArrayList<TraceNode>();
    for (var child : snapshot) {
      var info =
          new ConcurrencyInfo(
              groupId, child.threadName, child.threadId, child.virtual, ConcurrencyKind.FORK_JOIN);
      for (var root : child.roots) {
        var tagged = ConcurrencySupport.withConcurrency(root, info);
        context.emitTraceNode(tagged, parentSpanId);
        merged.add(tagged);
      }
    }
    context.onMerge(groupId, merged);
  }

  /**
   * Takes the copy this group will present, then ends the worker scope that produced it.
   *
   * <p><b>@edgeCase</b> The discard is unconditional, not guarded by {@code roots.isEmpty()}: a
   * scope can record spans and still produce no roots — at {@code ERRORS} a successful call is
   * pruned from the tree — and those are exactly the events nothing else would ever clear. The
   * scope was activated without adoption, so no thread's reportable set contains it and no {@code
   * reset()} can reach it.
   */
  private void collectRoots(Thread thread) {
    var roots = context.captureLocalTrace().roots();
    if (!roots.isEmpty()) {
      children.add(
          new CollectedChild(
              roots, thread.getName(), thread.getId(), ConcurrencySupport.isVirtual(thread)));
    }
    context.discardLocalTrace();
  }

  private record CollectedChild(
      List<TraceNode> roots, String threadName, long threadId, boolean virtual) {}
}
