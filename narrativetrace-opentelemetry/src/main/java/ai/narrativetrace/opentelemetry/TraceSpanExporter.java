/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.tree.TreeWalk;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Exports completed {@link TraceNode} trees as OpenTelemetry spans.
 *
 * <p>INTENT: Use this after capture when you already have a full trace tree and want to publish it
 * into an OpenTelemetry backend.
 *
 * <p><b>@llmNote</b> Parent-child structure is recreated by nesting span scopes during export. The
 * exporter does not reuse the original {@code SpanContext} ids as OTel span ids.
 *
 * <p><b>@edgeCase</b> Nodes with {@code TraceOutcome.Incomplete} are exported with outcome
 * attribute {@code "in-flight"}.
 *
 * <p><b>@edgeCase</b> Bounded and cycle-safe via {@link TreeWalk}, same as every core/clarity/
 * diagrams renderer: a hand-built or replayed {@code TraceNode} tree is not guaranteed acyclic, and
 * a genuinely deep one is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own span — closed immediately,
 * never a parent to anything — carrying a {@code narrative.truncated} attribute ({@code "cycle"} or
 * {@code "depth-limit"}) naming why the walk did not descend into it, the OTel-attribute equivalent
 * of the {@code "truncated"} JSON field {@code JsonExporter} sets for the same reason.
 */
public final class TraceSpanExporter {

  private static final AttributeKey<String> TRUNCATED =
      AttributeKey.stringKey("narrative.truncated");

  private final Tracer tracer;

  /**
   * Creates an exporter writing into the given tracer.
   *
   * @param tracer the OpenTelemetry tracer that receives one span per trace node
   */
  public TraceSpanExporter(Tracer tracer) {
    this.tracer = tracer;
  }

  /**
   * Exports a captured tree as OTel spans, depth first, parentage preserved.
   *
   * @param roots the tree's root nodes; an empty list exports nothing
   */
  public void export(List<TraceNode> roots) {
    Deque<OpenSpan> openSpans = new ArrayDeque<>();
    for (var root : roots) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (node, depth) -> enterNode(node, depth == 0, openSpans),
          (node, depth) -> exitNode(openSpans),
          (node, depth, reason) -> limitedNode(node, openSpans, reason));
    }
  }

  private void enterNode(TraceNode node, boolean isRoot, Deque<OpenSpan> openSpans) {
    emitChildEventOnParent(node, openSpans);
    var span = buildSpan(node, isRoot);
    openSpans.push(new OpenSpan(span, span.makeCurrent()));
  }

  private void exitNode(Deque<OpenSpan> openSpans) {
    var open = openSpans.pop();
    open.scope().close();
    open.span().end();
  }

  /** A node the walk stopped at instead of visiting: opened and closed immediately, as a leaf. */
  private void limitedNode(TraceNode node, Deque<OpenSpan> openSpans, TreeWalk.Reason reason) {
    emitChildEventOnParent(node, openSpans);
    var span = buildSpan(node, false);
    span.setAttribute(TRUNCATED, reason == TreeWalk.Reason.CYCLE ? "cycle" : "depth-limit");
    span.end();
  }

  private void emitChildEventOnParent(TraceNode node, Deque<OpenSpan> openSpans) {
    if (!openSpans.isEmpty()) {
      SpanContextAttributeMapper.emitChildEvent(openSpans.peek().span(), node);
    }
  }

  private Span buildSpan(TraceNode node, boolean isRoot) {
    var sig = node.signature();
    var span = tracer.spanBuilder(sig.className() + "." + sig.methodName()).startSpan();
    SpanContextAttributeMapper.setSpanAttributes(sig, span);
    SpanContextAttributeMapper.setTraceIdentityAttributes(node.spanContext(), span);
    SpanContextAttributeMapper.setNtSchemaAttributes(node.spanContext(), span);
    span.setAttribute(
        AttributeKey.doubleKey("narrative.duration_ms"), node.durationNanos() / 1_000_000.0);
    SpanContextAttributeMapper.setOutcomeAttributes(node.outcome(), span);
    SpanContextAttributeMapper.setConcurrencyAttributes(node.concurrency(), span);
    if (isRoot) {
      SpanContextAttributeMapper.setTraceLevelAttributes(node.spanContext(), span);
    }
    return span;
  }

  private record OpenSpan(Span span, Scope scope) {}
}
