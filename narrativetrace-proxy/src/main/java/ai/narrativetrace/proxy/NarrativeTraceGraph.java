/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;

/**
 * A shared-context scope for wiring a graph of traced collaborators.
 *
 * <p>INTENT: Plain-Java tests routinely wrap N collaborators with {@link NarrativeTraceProxy#trace}
 * against one {@link NarrativeContext}, then inject each traced proxy into the next implementation
 * so the whole call chain records into a single trace. Repeating {@code
 * NarrativeTraceProxy.trace(…, context)} for every collaborator is boilerplate. This scope owns the
 * context and turns each wrapping into one call, so a graph reads as its wiring:
 *
 * <pre>{@code
 * var graph = NarrativeTraceGraph.create();
 * Converter converter = graph.trace(new RealConverter(), Converter.class);
 * Ledger ledger = graph.trace(new RealLedger(converter), Ledger.class);
 * Service service = graph.trace(new RealService(ledger), Service.class);
 * service.run();
 * TraceTree tree = graph.context().captureTrace();
 * }</pre>
 *
 * <p><b>@llmNote</b> Proxies are created eagerly and returned immediately — there is no {@code
 * build()} step, because each traced proxy must be available to construct the next collaborator
 * that depends on it. The scope adds no tracing behaviour of its own; every {@code trace} delegates
 * verbatim to {@link NarrativeTraceProxy}, only supplying the shared context.
 *
 * <p><b>@sideEffects</b> None beyond what {@link NarrativeTraceProxy#trace} does; the scope holds
 * the context and nothing else.
 */
public final class NarrativeTraceGraph {

  private final NarrativeContext context;

  private NarrativeTraceGraph(NarrativeContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context must not be null");
    }
    this.context = context;
  }

  /**
   * Opens a scope backed by a fresh {@link ThreadLocalNarrativeContext}.
   *
   * @return a graph scope whose {@link #context()} is a new thread-scoped context
   */
  public static NarrativeTraceGraph create() {
    return new NarrativeTraceGraph(new ThreadLocalNarrativeContext());
  }

  /**
   * Opens a scope backed by an existing context — use this to graft onto a context created by a
   * JUnit extension or shared across scopes.
   *
   * @param context the context every traced collaborator records into
   * @return a graph scope over {@code context}
   * @throws IllegalArgumentException if {@code context} is null
   */
  public static NarrativeTraceGraph on(NarrativeContext context) {
    return new NarrativeTraceGraph(context);
  }

  /**
   * @return the shared context every collaborator wired through this scope records into
   */
  public NarrativeContext context() {
    return context;
  }

  /**
   * Wraps one collaborator in a tracing proxy bound to this scope's context.
   *
   * @param target concrete implementation receiving the real calls
   * @param interfaceType interface the proxy exposes
   * @param <T> the interface type
   * @return the traced proxy, ready to inject into collaborators that depend on it
   */
  public <T> T trace(T target, Class<T> interfaceType) {
    return NarrativeTraceProxy.trace(target, interfaceType, context);
  }

  /**
   * Wraps one collaborator that implements several interfaces in a tracing proxy bound to this
   * scope's context.
   *
   * @param target concrete implementation receiving the real calls
   * @param interfaces interfaces the proxy exposes; at least one is required
   * @return the traced proxy implementing all requested interfaces
   */
  public Object trace(Object target, Class<?>[] interfaces) {
    return NarrativeTraceProxy.trace(target, interfaces, context);
  }
}
