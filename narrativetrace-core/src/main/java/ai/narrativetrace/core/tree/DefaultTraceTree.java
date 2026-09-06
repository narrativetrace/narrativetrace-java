/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.List;

/**
 * Default immutable {@link TraceTree} implementation.
 *
 * <p>INTENT: Simple value object returned by the core builder and context implementations.
 *
 * <p><b>@llmNote</b> A tree with nodes in it <em>is</em> a trace and therefore always answers with
 * a trace id: the one its capturing context assigned, or — for a tree assembled by hand (tests,
 * replay tools, static scans) — one generated once, here, at construction. Generating at the tree
 * rather than at each exporter is what keeps a chapter and its own canonical entries naming the
 * same trace; two exporters resolving identity independently generated two. An <em>empty</em> tree
 * is not a trace and carries {@code null}: nothing ran, so there is nothing to identify.
 */
public final class DefaultTraceTree implements TraceTree {

  private final List<TraceNode> roots;
  private final TraceId traceId;
  private final TraceLoss loss;

  /** Creates a tree that generates its own trace id — the hand-built case. */
  public DefaultTraceTree(List<TraceNode> roots) {
    this(roots, null);
  }

  /**
   * Creates a tree carrying the trace id its capturing context assigned.
   *
   * @param roots root-level nodes, copied defensively
   * @param traceId the capture's trace id, or {@code null} to let a non-empty tree generate one
   */
  public DefaultTraceTree(List<TraceNode> roots, TraceId traceId) {
    this(roots, traceId, TraceLoss.none());
  }

  /**
   * Creates a tree carrying both its trace id and what its capture is missing.
   *
   * @param roots root-level nodes, copied defensively
   * @param traceId the capture's trace id, or {@code null} to let a non-empty tree generate one
   * @param loss what the capture lost; {@code null} is read as {@link TraceLoss#none()}, so a
   *     caller with nothing to say need not say it
   */
  public DefaultTraceTree(List<TraceNode> roots, TraceId traceId, TraceLoss loss) {
    this.roots = List.copyOf(roots);
    this.traceId = resolveTraceId(this.roots, traceId);
    this.loss = loss == null ? TraceLoss.none() : loss;
    assert this.roots.isEmpty() == (this.traceId == null)
        : "invariant: a tree has a trace id exactly when it has nodes";
  }

  private static TraceId resolveTraceId(List<TraceNode> roots, TraceId assigned) {
    if (roots.isEmpty()) {
      return null;
    }
    return assigned != null ? assigned : SpanIdGenerator.traceId();
  }

  @Override
  public List<TraceNode> roots() {
    return roots;
  }

  @Override
  public boolean isEmpty() {
    return roots.isEmpty();
  }

  @Override
  public TraceId traceId() {
    return traceId;
  }

  @Override
  public TraceLoss loss() {
    return loss;
  }
}
