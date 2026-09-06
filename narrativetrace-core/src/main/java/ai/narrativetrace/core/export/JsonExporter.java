/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.DurationFormat;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Flattens a trace tree into a JSON event stream.
 *
 * <p>INTENT: Use this for file output or downstream ingestion when consumers prefer JSON over the
 * human-oriented Markdown and text renderers.
 *
 * <p><b>@llmNote</b> Each node becomes two JSON objects: an enter event and a matching exit, error,
 * or incomplete event. Parent-child structure is preserved through generated numeric ids and {@code
 * parentId}, not through nested JSON objects.
 */
public final class JsonExporter {

  public String exportDocument(TraceTree tree, TraceMetadata metadata) {
    var sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"version\": \"1.0\",\n");
    sb.append("  \"scenario\": {\n");
    sb.append("    \"name\": \"").append(escapeJson(metadata.scenario())).append("\",\n");
    sb.append("    \"result\": \"").append(metadata.result().wireName()).append("\"");
    if (!tree.roots().isEmpty()) {
      // The scenario's wall-clock span, not the first root's duration: a scenario with async work
      // has more than one root, and the first one says nothing about how long the whole took.
      sb.append(",\n    \"durationMs\": ").append(DurationFormat.millis(tree.durationNanos()));
    }
    sb.append("\n");
    sb.append("  },\n");
    appendTraceBlock(tree, sb);
    sb.append("  \"events\": [\n");
    var ctx = new EmitContext();
    for (var root : tree.roots()) {
      flattenTree(root, ctx, sb);
    }
    sb.append("\n  ]\n}");
    return sb.toString();
  }

  public String export(TraceTree tree) {
    var sb = new StringBuilder();
    sb.append("{\n");
    appendTraceBlock(tree, sb);
    sb.append("  \"events\": [\n");
    var ctx = new EmitContext();
    for (var root : tree.roots()) {
      flattenTree(root, ctx, sb);
    }
    sb.append("\n  ]\n}");
    return sb.toString();
  }

  /**
   * Walks one root, bounded and cycle-safe via {@link TreeWalk}: a hand-built, replayed or
   * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
   * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
   * TreeWalk#MAX_DEPTH} or already on the current path still gets its own enter/exit event pair —
   * exactly as a leaf would — with a {@code "truncated"} field naming why the walk did not descend
   * into it.
   */
  private void flattenTree(TraceNode root, EmitContext ctx, StringBuilder sb) {
    Deque<String> spanIdChain = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> enterNode(node, depth, spanIdChain, ctx, sb),
        (node, depth) -> exitNode(node, depth, spanIdChain, sb, null),
        (node, depth, reason) -> limitedNode(node, depth, spanIdChain, reason, ctx, sb));
  }

  private void enterNode(
      TraceNode node, int depth, Deque<String> spanIdChain, EmitContext ctx, StringBuilder sb) {
    var parentSpanId = spanIdChain.peek();
    var spanId = resolveSpanId(node);
    appendEnterEvent(node, spanId, depth, parentSpanId, ctx, sb);
    spanIdChain.push(spanId);
  }

  private void exitNode(
      TraceNode node,
      int depth,
      Deque<String> spanIdChain,
      StringBuilder sb,
      TreeWalk.Reason marker) {
    var spanId = spanIdChain.pop();
    var parentSpanId = spanIdChain.peek();
    appendExitEvent(node, spanId, depth, parentSpanId, sb, marker);
  }

  /** A node the walk stopped at: entered and exited immediately, as if it were a leaf. */
  private void limitedNode(
      TraceNode node,
      int depth,
      Deque<String> spanIdChain,
      TreeWalk.Reason reason,
      EmitContext ctx,
      StringBuilder sb) {
    var parentSpanId = spanIdChain.peek();
    var spanId = resolveSpanId(node);
    appendEnterEvent(node, spanId, depth, parentSpanId, ctx, sb);
    appendExitEvent(node, spanId, depth, parentSpanId, sb, reason);
  }

  private void appendEnterEvent(
      TraceNode node,
      String spanId,
      int depth,
      String parentSpanId,
      EmitContext ctx,
      StringBuilder sb) {
    var sig = node.signature();
    ctx.appendSeparator(sb);
    sb.append("    {\n");
    appendCommonFields(sig, spanId, "enter", sb);
    sb.append("      \"parameters\": [");
    var params = sig.parameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) sb.append(", ");
      appendParamObject(params.get(i), sb);
    }
    sb.append("],\n");
    appendParentSpanId(node.spanContext(), sb);
    appendConcurrency(node.concurrency(), sb);
    appendFooterFields(depth, parentSpanId, sb);
    sb.append("    }");
  }

  private void appendExitEvent(
      TraceNode node,
      String spanId,
      int depth,
      String parentSpanId,
      StringBuilder sb,
      TreeWalk.Reason marker) {
    sb.append(",\n    {\n");
    appendOutcomeFields(node.outcome(), node.signature(), spanId, sb);
    // An unfinished span has no duration to report: null is "unknown", 0 would read as "instant".
    sb.append("      \"durationMs\": ")
        .append(
            node.outcome() instanceof TraceOutcome.Incomplete
                ? "null"
                : DurationFormat.millis(node.durationNanos()))
        .append(",\n");
    appendTruncated(marker, sb);
    appendFooterFields(depth, parentSpanId, sb);
    sb.append("    }");
  }

  /**
   * The walk-bound marker, present only on a node beyond {@link TreeWalk#MAX_DEPTH} or already on
   * the current path — absent on every ordinary event, so no existing document gains a field.
   */
  private void appendTruncated(TreeWalk.Reason marker, StringBuilder sb) {
    if (marker == null) {
      return;
    }
    var reason = marker == TreeWalk.Reason.CYCLE ? "cycle" : "depth-limit";
    sb.append("      \"truncated\": \"").append(reason).append("\",\n");
  }

  private void appendOutcomeFields(
      TraceOutcome outcome, MethodSignature sig, String spanId, StringBuilder sb) {
    appendCommonFields(sig, spanId, "exit", sb);
    if (outcome instanceof TraceOutcome.Returned r) {
      sb.append("      \"outcome\": \"returned\",\n");
      if (r.renderedValue() != null) {
        sb.append("      \"returnValue\": \"")
            .append(escapeJson(r.renderedValue()))
            .append("\",\n");
      }
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append("      \"outcome\": \"threw\",\n");
      appendErrorDetail(t, sb);
    } else {
      // TraceOutcome is sealed, so this is Incomplete or a synthetic launcher node carrying no
      // outcome at all (TraceNode.outcome is null for those). Neither returned; saying they did is
      // a claim the reader cannot check. Matches TraceTreeCanonicalMapper.outcomeName.
      sb.append("      \"outcome\": \"incomplete\",\n");
    }
  }

  private void appendErrorDetail(TraceOutcome.Threw t, StringBuilder sb) {
    sb.append("      \"errorType\": \"")
        .append(escapeJson(t.exception().getClass().getSimpleName()))
        .append("\",\n");
    sb.append("      \"errorMessage\": \"")
        .append(escapeJson(ExceptionMessage.text(t.exception())))
        .append("\",\n");
  }

  private void appendCommonFields(
      MethodSignature sig, String spanId, String type, StringBuilder sb) {
    sb.append("      \"spanId\": \"").append(spanId).append("\",\n");
    sb.append("      \"type\": \"").append(type).append("\",\n");
    sb.append("      \"className\": \"").append(escapeJson(sig.className())).append("\",\n");
    sb.append("      \"methodName\": \"").append(escapeJson(sig.methodName())).append("\",\n");
  }

  /**
   * Writes the {@code trace} block, always: identity comes from the shared {@link TraceIdentity},
   * so this document names the same trace as the chapter that embeds it and as the canonical
   * entries flattened from the same tree.
   *
   * <p><b>@llmNote</b> This used to resolve identity for itself, scanning <em>roots only</em> and
   * omitting the whole block when it found nothing. Both halves were divergences from the other two
   * emitters: a mixed tree whose context sits on a child resolved to "no trace" here while they
   * inherited it, and a span-less capture produced a chapter naming a trace with a tree inside it
   * naming none. {@code chapter-tree.schema.json} marks the block optional, so emitting it always
   * is legal — the schema is a floor, not the contract between the three emitters.
   */
  private void appendTraceBlock(TraceTree tree, StringBuilder sb) {
    var identity = TraceIdentity.of(tree);
    sb.append("  \"trace\": {\n");
    sb.append("    \"traceId\": \"").append(identity.traceId()).append("\"");
    appendTraceField("traceName", identity.traceName(), sb);
    appendInheritedContext(identity.inherited(), sb);
    sb.append("\n  },\n");
  }

  /**
   * The request-scoped fields, written only when the tree actually carries a span context. A
   * generated identity knows which trace this is and nothing about who called it, and inventing a
   * service or a client IP would be worse than omitting them.
   */
  private static void appendInheritedContext(SpanContext inherited, StringBuilder sb) {
    if (inherited == null) {
      return;
    }
    appendTraceField("serviceName", inherited.serviceName(), sb);
    appendTraceField("serviceVersion", inherited.serviceVersion(), sb);
    appendTraceField("environment", inherited.environment(), sb);
    appendTraceField("httpMethod", inherited.httpMethod(), sb);
    appendTraceField("httpRoute", inherited.httpRoute(), sb);
    appendTraceField("clientIp", inherited.clientIp(), sb);
    appendTraceField("enduserId", inherited.enduserId(), sb);
    appendTraceField("sessionId", inherited.sessionId(), sb);
    appendTraceField("tenantId", inherited.tenantId(), sb);
  }

  private static void appendTraceField(String key, Object value, StringBuilder sb) {
    if (value != null) {
      sb.append(",\n    \"")
          .append(key)
          .append("\": \"")
          .append(JsonEscape.escape(String.valueOf(value)))
          .append("\"");
    }
  }

  /** Appends parentSpanId per event when present. */
  private void appendParentSpanId(SpanContext sc, StringBuilder sb) {
    if (sc == null || sc.parentSpanId() == null) {
      return;
    }
    appendOptionalField("parentSpanId", sc.parentSpanId().toString(), sb);
  }

  private static void appendOptionalField(String key, Object value, StringBuilder sb) {
    if (value != null) {
      sb.append("      \"")
          .append(key)
          .append("\": \"")
          .append(JsonEscape.escape(String.valueOf(value)))
          .append("\",\n");
    }
  }

  private void appendConcurrency(ConcurrencyInfo info, StringBuilder sb) {
    if (info == null) {
      return;
    }
    sb.append("      \"concurrency\": {\n");
    sb.append("        \"groupId\": \"").append(JsonEscape.escape(info.groupId())).append("\",\n");
    sb.append("        \"threadName\": \"")
        .append(JsonEscape.escape(info.threadName()))
        .append("\",\n");
    sb.append("        \"threadId\": ").append(info.threadId()).append(",\n");
    sb.append("        \"virtual\": ").append(info.virtual()).append(",\n");
    sb.append("        \"kind\": \"").append(kebabCase(info.kind())).append("\"\n");
    sb.append("      },\n");
  }

  private void appendFooterFields(int depth, String parentSpanId, StringBuilder sb) {
    sb.append("      \"depth\": ").append(depth).append(",\n");
    sb.append("      \"parentId\": ")
        .append(parentSpanId == null ? "null" : "\"" + parentSpanId + "\"")
        .append("\n");
  }

  private void appendParamObject(ParameterCapture param, StringBuilder sb) {
    sb.append("{\"name\": \"").append(escapeJson(param.name())).append("\", ");
    if (param.redacted()) {
      sb.append("\"value\": \"[REDACTED]\", \"redacted\": true}");
    } else {
      sb.append("\"value\": \"")
          .append(escapeJson(param.renderedValue()))
          .append("\", \"redacted\": false}");
    }
  }

  private static String resolveSpanId(TraceNode node) {
    if (node.spanContext() != null) {
      return node.spanContext().spanId().toString();
    }
    return String.valueOf(System.identityHashCode(node));
  }

  private static String kebabCase(ConcurrencyKind kind) {
    return kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
  }

  private String escapeJson(String s) {
    return JsonEscape.escape(s);
  }

  private static final class EmitContext {
    private boolean first = true;

    void appendSeparator(StringBuilder sb) {
      if (!first) sb.append(",\n");
      first = false;
    }
  }
}
