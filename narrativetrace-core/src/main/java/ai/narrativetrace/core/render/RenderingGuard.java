/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Per-thread depth marking that a {@link ValueRenderer} entry point is executing on the calling
 * thread right now.
 *
 * <p>INTENT: {@link ValueRenderer} reflectively invokes record accessors while rendering a
 * parameter or return value. When the accessor's declaring class is woven by the agent, that
 * reflective call runs the accessor's own instrumented bytecode — opening a span for a call the
 * application never made, and one attributed as a root because rendering happens outside any traced
 * scope: before {@code beginScope} for a parameter (rendered while building the enter event), after
 * {@code endScope} for a return value (rendered after the traced method's own scope already
 * closed). This guard is how the instrumented gate recognizes "I am being invoked back from inside
 * my own rendering" and answers with the untraced fast path instead of opening a span. The proxy
 * path has the identical exposure through its own {@code ValueRenderer} instance, which is exactly
 * why this lives here rather than in the agent module: a real deployment can have both engines
 * attached to the same JVM and thread, and the guard must be shared by call, not by renderer
 * instance.
 *
 * <p><b>@llmNote</b> A depth counter, not one boolean: the guard nests. A woven accessor reached
 * reflectively is stopped at its own gate and never calls back into {@code ValueRenderer}, but the
 * narrative-summary hook is a sanctioned door into user code, invoked by the renderer on purpose,
 * and that code may render a value of its own — another {@code ValueRenderer}, a proxied
 * collaborator whose parameters are rendered, an SLF4J listener, a {@code @Narrated} template
 * resolved through {@code TemplateParser.accessProperty}. Each runs its own {@link #enter()}/{@link
 * #leave()} pair inside the outer one, so {@link #leave()} restores what {@link #enter()} found and
 * the guard stays up for the rest of the outer walk (ADV-2026-10-23-2).
 *
 * <p><b>@sideEffects</b> The last {@link #leave()} removes the thread-local entry rather than
 * storing a zero — this runs on the application thread once per rendered value, so it must not
 * leave a live map entry behind for every thread that ever rendered a value once. A {@link
 * #leave()} with no open entry is a no-op, never a negative depth.
 */
public final class RenderingGuard {

  private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

  private RenderingGuard() {}

  /**
   * Whether the calling thread is currently inside a {@link ValueRenderer} entry point.
   *
   * <p><b>@llmNote</b> Read by the instrumentation gate ({@code AgentRuntime.isActive()}) before
   * anything else on every instrumented call; keep this a plain thread-local read, never anything
   * that can throw or block.
   */
  public static boolean isActive() {
    return DEPTH.get() != null;
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
    var depth = DEPTH.get();
    DEPTH.set(depth == null ? 1 : depth + 1);
  }

  /**
   * Closes the calling thread's innermost rendering entry, restoring the state {@link #enter()}
   * found. Always called from a finally block.
   */
  public static void leave() {
    var depth = DEPTH.get();
    if (depth == null || depth <= 1) {
      DEPTH.remove();
    } else {
      DEPTH.set(depth - 1);
    }
  }
}
