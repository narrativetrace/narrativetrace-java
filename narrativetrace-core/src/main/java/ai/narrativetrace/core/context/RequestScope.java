/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

/**
 * One request's own trace state, owned by the code serving the request rather than by a thread.
 *
 * <p>INTENT: A reactive server runs many requests on one event-loop thread, interleaved: request B
 * starts while request A waits for its response. A thread-local trace state shared by both lets B's
 * start reset A, A's export capture B, and A's cleanup erase B. A request scope gives each request
 * its own state and makes it current only for the segments the request's own code runs in.
 *
 * <p><b>@howToUse</b> Open one per request ({@link NarrativeContext#openRequestScope()}); wrap
 * every segment that runs on behalf of the request — stamping request metadata, invoking the
 * handler, capturing and exporting — in {@code try (var active = scope.activate()) { ... }}; call
 * {@link #end()} once when the request is over.
 *
 * <p><b>@threadSafety</b> A scope may be activated on any thread, one segment at a time. Work the
 * request hands to another thread (a scheduler hop, an executor) still propagates through {@link
 * NarrativeContext#snapshot()}, taken inside an active segment.
 */
public interface RequestScope {

  /**
   * Makes this request's trace state current on the calling thread.
   *
   * @return a handle whose {@code close()} puts back exactly what the thread held before
   */
  ContextScope activate();

  /**
   * Ends the request: discards its trace state as {@link NarrativeContext#reset()} discards a
   * thread's, without touching the calling thread's own state. Calling it twice does nothing more.
   */
  void end();

  /**
   * The scope for a context with no per-request state of its own: the request shares the thread's
   * state, reset when the scope opens and when it ends — exactly the lifecycle a filter calling
   * {@code reset()} at both ends had.
   *
   * @param context the context whose thread state the request shares
   * @return a scope over the thread's shared state
   */
  static RequestScope sharedThreadState(NarrativeContext context) {
    context.reset();
    return new RequestScope() {
      @Override
      public ContextScope activate() {
        return () -> {};
      }

      @Override
      public void end() {
        context.reset();
      }
    };
  }
}
