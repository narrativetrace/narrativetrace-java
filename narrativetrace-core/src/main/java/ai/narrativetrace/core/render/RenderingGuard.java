/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Per-thread flag marking that a {@link ValueRenderer} entry point is executing on the calling
 * thread right now.
 *
 * <p>INTENT: {@link ValueRenderer} reflectively invokes record accessors while rendering a
 * parameter or return value. When the accessor's declaring class is woven by the agent, that
 * reflective call runs the accessor's own instrumented bytecode — opening a span for a call the
 * application never made, and one attributed as a root because rendering happens outside any traced
 * scope: before {@code beginScope} for a parameter (rendered while building the enter event), after
 * {@code endScope} for a return value (rendered after the traced method's own scope already
 * closed). This flag is how the instrumented gate recognizes "I am being invoked back from inside
 * my own rendering" and answers with the untraced fast path instead of opening a span. The proxy
 * path has the identical exposure through its own {@code ValueRenderer} instance, which is exactly
 * why this lives here rather than in the agent module: a real deployment can have both engines
 * attached to the same JVM and thread, and the flag must be shared by call, not by renderer
 * instance.
 *
 * <p><b>@llmNote</b> One boolean, not a counter, is enough. {@link ValueRenderer}'s public entry
 * points never observe re-entry on the same thread: a reflective call into a woven accessor made
 * while the guard is active is stopped at the instrumented method's own gate before it can call
 * back into {@code ValueRenderer}, so the guard is never asked to nest.
 *
 * <p><b>@sideEffects</b> {@link #leave()} removes the thread-local entry rather than resetting it
 * to {@code false} — this runs on the application thread once per rendered value, so it must not
 * leave a live map entry behind for every thread that ever rendered a value once.
 */
public final class RenderingGuard {

  private static final ThreadLocal<Boolean> RENDERING = new ThreadLocal<>();

  private RenderingGuard() {}

  /**
   * Whether the calling thread is currently inside a {@link ValueRenderer} entry point.
   *
   * <p><b>@llmNote</b> Read by the instrumentation gate ({@code AgentRuntime.isActive()}) before
   * anything else on every instrumented call; keep this a plain thread-local read, never anything
   * that can throw or block.
   */
  public static boolean isActive() {
    return RENDERING.get() != null;
  }

  /**
   * Marks the calling thread as rendering. Always paired with {@link #leave()} in a finally.
   *
   * <p><b>@llmNote</b> Public so a caller outside {@code ValueRenderer} can wrap its own reflective
   * state read the same way — {@code TemplateParser.accessProperty} is the other caller, for the
   * identical reason: it reflectively reads a record's backing field or, for a genuinely computed
   * property with no backing field, invokes the property's accessor method, and that invocation can
   * run woven bytecode exactly as a record accessor invoked from {@code ValueRenderer} can.
   */
  public static void enter() {
    RENDERING.set(Boolean.TRUE);
  }

  /** Clears the calling thread's rendering flag. Always called from a finally block. */
  public static void leave() {
    RENDERING.remove();
  }
}
