/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micrometer;

import ai.narrativetrace.core.context.ContextScope;
import ai.narrativetrace.core.context.ContextSnapshot;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * Micrometer {@link ThreadLocalAccessor} for NarrativeTrace snapshots.
 *
 * <p>INTENT: Register this accessor when Micrometer context propagation should automatically carry
 * the active NarrativeTrace snapshot across threads.
 *
 * <p><b>@llmNote</b> Calling {@link #setValue(ContextSnapshot)} closes any previously active scope
 * held by this accessor before activating the new snapshot.
 */
public class NarrativeTraceThreadLocalAccessor implements ThreadLocalAccessor<ContextSnapshot> {

  /** The key Micrometer's context registry stores this accessor's value under. */
  public static final String KEY = "narrativetrace";

  private final ThreadLocalNarrativeContext context;
  private final ThreadLocal<ContextScope> currentScope = new ThreadLocal<>();

  /**
   * Creates an accessor over the application's own context — the form to prefer.
   *
   * @param context the context whose snapshots cross threads
   */
  public NarrativeTraceThreadLocalAccessor(ThreadLocalNarrativeContext context) {
    this.context = context;
  }

  /**
   * Creates an accessor with a standalone context. <b>Warning:</b> this context is disconnected
   * from any application context. Prefer {@link
   * #NarrativeTraceThreadLocalAccessor(ThreadLocalNarrativeContext)} and pass the same context
   * instance used by the rest of the application.
   */
  public NarrativeTraceThreadLocalAccessor() {
    this(new ThreadLocalNarrativeContext());
  }

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public ContextSnapshot getValue() {
    return context.snapshot();
  }

  @Override
  public void setValue(ContextSnapshot snapshot) {
    var previous = currentScope.get();
    if (previous != null) {
      previous.close();
    }
    var scope = snapshot.activate();
    currentScope.set(scope);
  }

  @Override
  public void setValue() {
    var scope = currentScope.get();
    if (scope != null) {
      scope.close();
      currentScope.remove();
    }
  }
}
