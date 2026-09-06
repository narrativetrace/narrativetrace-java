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
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Helper for background work that outlives the parent call.
 *
 * <p>INTENT: Use this when the launching method should continue immediately instead of waiting for
 * background work. Creation injects a synthetic launcher node into the parent trace; wrapped tasks
 * later record their own roots with {@link ConcurrencyKind#FIRE_AND_FORGET}.
 *
 * <p><b>@llmNote</b> There is no merge step. Consumers that need the background tree must read it
 * from {@link #childRoots()} or via renderers that understand fire-and-forget grouping.
 *
 * <p><b>@sideEffects</b> A wrapped task ends by taking a tagged copy of its own trace and
 * discarding the raw scope behind it; {@link #childRoots()} is the surviving record. Keeping the
 * raw spans as well would leak them — the worker scope is activated without adoption, so no
 * thread's reportable set contains it and no {@code reset()} could ever clear it.
 */
public final class FireAndForgetGroup {

  private static final AtomicLong ID_GENERATOR = new AtomicLong();

  private final NarrativeContext context;
  private final String groupId;
  private final List<TraceNode> collectedRoots = new CopyOnWriteArrayList<>();

  private FireAndForgetGroup(NarrativeContext context, String groupId) {
    this.context = context;
    this.groupId = groupId;
  }

  public static FireAndForgetGroup create(NarrativeContext context, String launchingClassName) {
    var group = new FireAndForgetGroup(context, "fanf-" + ID_GENERATOR.incrementAndGet());
    context.emitTraceNode(
        createLauncherNode(group.groupId, launchingClassName), context.currentSpanId());
    context.onFireAndForgetLaunched(group.groupId);
    return group;
  }

  private static TraceNode createLauncherNode(String groupId, String className) {
    var thread = Thread.currentThread();
    var signature = new MethodSignature(className, "fire-and-forget", List.of());
    var info =
        new ConcurrencyInfo(
            groupId,
            thread.getName(),
            thread.getId(),
            ConcurrencySupport.isVirtual(thread),
            ConcurrencyKind.FIRE_AND_FORGET);
    return new TraceNode(signature, List.of(), null, 0L, 0L, info);
  }

  public <T> Supplier<T> wrap(Supplier<T> task) {
    var snapshot = context.snapshot();
    return () -> {
      var thread = Thread.currentThread();
      try (var scope = snapshot.activateWithoutAdoption()) {
        try {
          return task.get();
        } finally {
          collectAndTagRoots(thread);
        }
      }
    };
  }

  public List<TraceNode> childRoots() {
    return List.copyOf(collectedRoots);
  }

  /**
   * Takes the tagged copy this group hands out, then ends the worker scope that produced it.
   *
   * <p><b>@edgeCase</b> The discard is unconditional for the same reason it is in {@link
   * ForkGroup}: a scope that produced no roots can still have recorded events, and nothing else
   * will ever clear them.
   */
  private void collectAndTagRoots(Thread thread) {
    var roots = context.captureLocalTrace().roots();
    if (!roots.isEmpty()) {
      var info =
          new ConcurrencyInfo(
              groupId,
              thread.getName(),
              thread.getId(),
              ConcurrencySupport.isVirtual(thread),
              ConcurrencyKind.FIRE_AND_FORGET);
      for (var root : roots) {
        collectedRoots.add(ConcurrencySupport.withConcurrency(root, info));
      }
    }
    context.discardLocalTrace();
  }

  public String groupId() {
    return groupId;
  }
}
