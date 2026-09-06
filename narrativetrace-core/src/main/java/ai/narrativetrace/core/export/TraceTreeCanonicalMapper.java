/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.ExceptionMessage;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.core.tree.TreeWalk;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Maps a finished {@link TraceTree} to the flat canonical entry list (schema 1.1).
 *
 * <p>INTENT: The per-test canonical JSON artifact — the input of trace translation — is derived
 * from the captured tree after the test, not from the live event stream. Each node yields one
 * {@code method_enter} and one {@code method_exit} entry in depth-first order, linked by span ids.
 * Nodes without a {@link ai.narrativetrace.api.event.SpanContext} (plain unit-test trees) get
 * deterministic synthetic span ids, so nesting depth survives the flattening either way; their
 * trace-scoped identity comes from {@link TraceIdentity}, the one resolution {@link
 * ChapterExporter} shares, so a chapter and its own entries always name the same trace.
 *
 * <p><b>@llmNote</b> Unlike {@link CanonicalEntryMapper#fromEvent}, exit entries here carry the
 * node's real {@code code.namespace}/{@code code.function} — the tree still knows its signature,
 * whereas exit events only know their span name.
 */
public final class TraceTreeCanonicalMapper {

  private TraceTreeCanonicalMapper() {}

  /**
   * Flattens the tree into enter/exit entries, depth-first, roots in order.
   *
   * <p><b>@edgeCase</b> Walked through {@link TreeWalk}: a hand-built, replayed or deserialized
   * tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list), and a
   * genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
   * TreeWalk#MAX_DEPTH} or already on the current path still yields its own enter/exit entry pair —
   * exactly as a leaf would — with {@link TreeWalk.Reason#marker()} appended to the exit message;
   * the walk simply never descends into its children.
   */
  public static List<CanonicalEntry> fromTree(TraceTree tree) {
    if (tree == null) {
      throw new IllegalArgumentException("tree must not be null");
    }
    var entries = new ArrayList<CanonicalEntry>();
    var counter = new int[] {0};
    var identity = TraceIdentity.of(tree);
    for (var root : tree.roots()) {
      appendTree(entries, root, counter, identity);
    }
    return List.copyOf(entries);
  }

  private static void appendTree(
      List<CanonicalEntry> entries, TraceNode root, int[] counter, TraceIdentity identity) {
    Deque<String> spanIdChain = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> enterNode(entries, node, spanIdChain, counter, identity),
        (node, depth) -> exitNode(entries, node, spanIdChain, identity, null),
        (node, depth, reason) ->
            appendLimitedNode(entries, node, spanIdChain, counter, identity, reason));
  }

  private static void enterNode(
      List<CanonicalEntry> entries,
      TraceNode node,
      Deque<String> spanIdChain,
      int[] counter,
      TraceIdentity identity) {
    var parentSpanId = spanIdChain.peek();
    var spanId = spanIdOf(node, counter);
    entries.add(enterEntry(node, spanId, parentSpanId, identity));
    spanIdChain.push(spanId);
  }

  private static void exitNode(
      List<CanonicalEntry> entries,
      TraceNode node,
      Deque<String> spanIdChain,
      TraceIdentity identity,
      TreeWalk.Reason marker) {
    var spanId = spanIdChain.pop();
    var parentSpanId = spanIdChain.peek();
    entries.add(exitEntry(node, spanId, parentSpanId, identity, marker));
  }

  /** A node the walk stopped at: entered and exited immediately, as if it were a leaf. */
  private static void appendLimitedNode(
      List<CanonicalEntry> entries,
      TraceNode node,
      Deque<String> spanIdChain,
      int[] counter,
      TraceIdentity identity,
      TreeWalk.Reason reason) {
    var parentSpanId = spanIdChain.peek();
    var spanId = spanIdOf(node, counter);
    entries.add(enterEntry(node, spanId, parentSpanId, identity));
    entries.add(exitEntry(node, spanId, parentSpanId, identity, reason));
  }

  private static String spanIdOf(TraceNode node, int[] counter) {
    if (node.spanContext() != null) {
      return node.spanContext().spanId().toString();
    }
    return String.format("%016x", ++counter[0]);
  }

  private static CanonicalEntry enterEntry(
      TraceNode node, String spanId, String parentSpanId, TraceIdentity identity) {
    var sig = node.signature();
    var thread = node.thread();
    return commonFields(CanonicalEntry.builder(), node, spanId, parentSpanId, identity)
        .threadName(thread != null ? thread.threadName() : null)
        .threadId(thread != null ? thread.threadId() : null)
        .ntThreadVirtual(thread != null ? thread.virtual() : null)
        .timestamp(timestamp(node.startTimeNanos(), node))
        .level("trace")
        .message(enterMessage(sig))
        .ntEventType("method_enter")
        .ntReturnType(sig.returnType())
        .ntInstanceId(sig.instanceId())
        .codeFilepath(sig.source() != null ? sig.source().file() : null)
        .codeLineno(sig.source() != null ? sig.source().line() : null)
        .ntParameters(parameters(sig))
        .ntNarrationTemplate(sig.narrationTemplate())
        .build();
  }

  private static CanonicalEntry exitEntry(
      TraceNode node,
      String spanId,
      String parentSpanId,
      TraceIdentity identity,
      TreeWalk.Reason marker) {
    var sig = node.signature();
    var outcome = node.outcome();
    return commonFields(CanonicalEntry.builder(), node, spanId, parentSpanId, identity)
        .timestamp(timestamp(node.startTimeNanos() + node.durationNanos(), node))
        .level(outcome instanceof TraceOutcome.Threw ? "error" : "trace")
        .message(exitMessage(sig, outcome, marker))
        .ntEventType("method_exit")
        .ntOutcome(outcomeName(outcome))
        .durationMs(TimeUnit.NANOSECONDS.toMillis(node.durationNanos()))
        .ntReturnValue(outcome instanceof TraceOutcome.Returned r ? r.renderedValue() : null)
        .exceptionType(
            outcome instanceof TraceOutcome.Threw t
                ? t.exception().getClass().getSimpleName()
                : null)
        .exceptionMessage(
            outcome instanceof TraceOutcome.Threw t ? ExceptionMessage.of(t.exception()) : null)
        .ntExceptionPackage(
            outcome instanceof TraceOutcome.Threw t
                ? CanonicalEntryMapper.packageOf(t.exception().getClass())
                : null)
        .build();
  }

  /** Identity and correlation fields shared by the enter and exit entries of one node. */
  private static CanonicalEntry.Builder commonFields(
      CanonicalEntry.Builder b,
      TraceNode node,
      String spanId,
      String parentSpanId,
      TraceIdentity identity) {
    var sig = node.signature();
    var sc = node.spanContext();
    // Item 26b: trace-scoped fields are read off the node's OWN context when it has one,
    // and off the trace's inherited context otherwise -- never regenerated per node, or a
    // context-free child would split its own trace's identity.
    var effective = sc != null ? sc : identity.inherited();
    var traceId = traceId(node, identity);
    return b.traceId(traceId.toString())
        .ntStoryId(sc != null && sc.storyId() != null ? sc.storyId() : identity.storyId())
        .ntChapterId(sc != null && sc.chapterId() != null ? sc.chapterId() : identity.chapterId())
        .ntTraceName(TraceNamer.name(traceId.value()))
        .service(CanonicalEntryMapper.serviceNameOrUnknown(effective))
        .environment(effective != null ? effective.environment() : null)
        .hostName(effective != null ? effective.hostName() : null)
        .processPid(effective != null ? effective.processPid() : null)
        .runtimeVersion(effective != null ? effective.runtimeVersion() : null)
        .spanId(spanId)
        .parentSpanId(parentSpanId)
        .codeNamespace(sig.className())
        .codeFunction(sig.methodName())
        .ntPackage(sig.packageName())
        .ntEntryType("entry")
        .ntSchemaVersion(CanonicalEntry.SCHEMA_VERSION);
  }

  private static String outcomeName(TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Returned) return "success";
    if (outcome instanceof TraceOutcome.Threw) return "failure";
    return "incomplete";
  }

  /** The node's own trace id when it kept a span context, else the one its tree resolved. */
  private static TraceId traceId(TraceNode node, TraceIdentity identity) {
    return node.spanContext() != null ? node.spanContext().traceId() : identity.traceId();
  }

  /**
   * Node clocks are monotonic nanos with an arbitrary origin. Nodes whose span context carries the
   * per-trace wall-clock anchor get real timestamps; plain unit-test trees (no span context) keep
   * the synthetic epoch base.
   */
  private static String timestamp(long nanos, TraceNode node) {
    var sc = node.spanContext();
    if (sc != null && sc.traceAnchor() != null) {
      return Instant.ofEpochMilli(sc.traceAnchor().toEpochMillis(nanos)).toString();
    }
    return Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(nanos)).toString();
  }

  private static String enterMessage(MethodSignature sig) {
    return "→ " + sig.className() + "." + sig.methodName();
  }

  private static String exitMessage(
      MethodSignature sig, TraceOutcome outcome, TreeWalk.Reason marker) {
    var base = plainExitMessage(sig, outcome);
    return marker != null ? base + " " + marker.marker() : base;
  }

  private static String plainExitMessage(MethodSignature sig, TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Threw t) {
      return "!! "
          + t.exception().getClass().getSimpleName()
          + ": "
          + ExceptionMessage.text(t.exception());
    }
    return "← " + sig.className() + "." + sig.methodName();
  }

  private static List<ParameterEntry> parameters(MethodSignature sig) {
    if (sig.parameters().isEmpty()) {
      return null;
    }
    return sig.parameters().stream()
        .map(
            p ->
                new ParameterEntry(
                    p.name(),
                    p.redacted() ? "[REDACTED]" : p.renderedValue(),
                    p.redacted(),
                    p.type()))
        .toList();
  }
}
