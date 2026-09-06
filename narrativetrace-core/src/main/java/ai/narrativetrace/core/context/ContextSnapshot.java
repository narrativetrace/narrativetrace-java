/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Captured trace continuation that can be activated on another thread.
 *
 * <p>INTENT: Use this when asynchronous work should remain attached to the current trace. A
 * snapshot carries the trace id and parent span relationship, not the originating thread's live
 * stack.
 *
 * <p><b>@pattern</b> Scope-based propagation handle. Activate it around work that should inherit
 * trace lineage.
 *
 * <p>Convenience methods {@link #wrap(Runnable)}, {@link #wrap(Callable)}, and {@link
 * #wrap(Supplier)} exist specifically for executor and {@code CompletableFuture} call sites:
 *
 * <pre>{@code
 * var snapshot = context.snapshot();
 * executor.submit(snapshot.wrap(() -> {
 *     // trace events here are captured in the parent context
 *     service.processOrder(orderId);
 * }));
 * }</pre>
 *
 * @see NarrativeContext#snapshot()
 * @see ContextScope
 */
public interface ContextSnapshot {

  /**
   * Activates this snapshot on the current thread.
   *
   * <p><b>@llmNote</b> Always close the returned scope. Forgetting to do so leaks the propagated
   * stack into later work executed on the same thread.
   *
   * @return a closeable scope that restores the previous context on close
   */
  ContextScope activate();

  /**
   * Activates without adoption: work traced under this scope does <em>not</em> join the
   * snapshotting thread's captured trace, because the caller reports it itself.
   *
   * <p>INTENT: For helpers that own their children's presentation — {@code ForkGroup.merge()}
   * re-emits them under the fork's parent span, {@code FireAndForgetGroup} hands them out through
   * {@code childRoots()} behind a launcher marker. Adopting as well would show the same work twice,
   * or make a parent's tree depend on when a detached child happened to finish.
   *
   * <p>Ordinary propagation — Micrometer, a Spring {@code TaskDecorator}, any manual snapshot —
   * wants {@link #activate()}: the async work belongs to the trace that launched it.
   */
  default ContextScope activateWithoutAdoption() {
    return activate();
  }

  /**
   * Wraps a {@link Runnable} to activate this snapshot before execution.
   *
   * @param task the task to wrap
   * @return a wrapped task that activates and deactivates the snapshot
   */
  default Runnable wrap(Runnable task) {
    return () -> {
      try (var scope = activate()) {
        task.run();
      }
    };
  }

  /**
   * Wraps a {@link Callable} to activate this snapshot before execution.
   *
   * @param task the task to wrap
   * @param <T> the return type
   * @return a wrapped task that activates and deactivates the snapshot
   */
  default <T> Callable<T> wrap(Callable<T> task) {
    return () -> {
      try (var scope = activate()) {
        return task.call();
      }
    };
  }

  /**
   * Wraps a {@link Supplier} to activate this snapshot before execution.
   *
   * @param task the task to wrap
   * @param <T> the return type
   * @return a wrapped task that activates and deactivates the snapshot
   */
  default <T> Supplier<T> wrap(Supplier<T> task) {
    return () -> {
      try (var scope = activate()) {
        return task.get();
      }
    };
  }
}
