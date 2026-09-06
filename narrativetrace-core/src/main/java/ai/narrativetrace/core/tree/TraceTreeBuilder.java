/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds immutable trace trees from append-only events.
 *
 * <p>INTENT: This is the translation layer between the event model used during capture and the tree
 * model used for rendering and export.
 *
 * <p><b>@llmNote</b> Tree pruning ({@code ERRORS} and {@code SUMMARY}) is applied here, but
 * parameter suppression happens earlier during event capture. You cannot retroactively recover
 * {@code DETAIL}-level parameter values from events captured at {@code NARRATIVE} or below.
 *
 * <p><b>@edgeCase</b> Every recursive step below — construction and both pruning passes — is
 * bounded and cycle-safe, for the same reason {@link TreeWalk} is: this runs on <em>every</em>
 * capture at every level except {@code OFF}, before any renderer's own bound ever gets a chance to
 * apply. A real recursive business method can legitimately produce thousands of nested spans, and a
 * malformed or replayed event stream is not guaranteed to have exactly one parent per span id (see
 * {@link #buildNodeRecursive}); either one must degrade to a truncation marker, never a crash, this
 * early in the pipeline.
 */
public final class TraceTreeBuilder {

  private TraceTreeBuilder() {}

  /** Builds a tree with no assigned trace id — for callers that have no context to ask. */
  public static TraceTree build(List<TraceEvent> events, TracingLevel level) {
    return build(events, level, null);
  }

  /**
   * Builds the tree, stamping it with the trace id the capturing context assigned.
   *
   * <p>INTENT: The tree, not the node, is what an exporter asks for a trace's identity. Nodes that
   * carry a span context still answer for themselves; this is what lets a tree whose nodes carry
   * none be exported as the one trace it is, rather than as one trace per exporter.
   *
   * @param traceId the capture's trace id, or {@code null} when the caller has none
   */
  public static TraceTree build(List<TraceEvent> events, TracingLevel level, TraceId traceId) {
    return build(events, level, traceId, TraceLoss.none());
  }

  /**
   * Builds the tree, stamping it with the trace id and with what the capture is missing.
   *
   * <p>INTENT: Loss belongs to the capture, not to any node in it, so it rides on the tree — which
   * is what lets every renderer say "this narrative is incomplete" without the caller threading a
   * second argument through every render call.
   *
   * @param loss what the capturing context reports lost; {@link TraceLoss#none()} for a clean run
   */
  public static TraceTree build(
      List<TraceEvent> events, TracingLevel level, TraceId traceId, TraceLoss loss) {
    if (level == TracingLevel.OFF) {
      return new DefaultTraceTree(List.of(), traceId, loss);
    }

    var index = EventIndex.of(events);

    var built = new ArrayList<TraceNode>();
    for (var spanId : index.rootSpanIds) {
      built.add(buildNodeRecursive(spanId, index));
    }
    List<TraceNode> roots = built;
    if (level == TracingLevel.ERRORS) {
      roots = retainErrorPaths(built);
    } else if (level == TracingLevel.SUMMARY) {
      roots = pruneSummary(built);
    }

    return new DefaultTraceTree(roots, traceId, loss);
  }

  /**
   * Builds the node tree rooted at {@code rootSpanId}, bottom-up, one call per genuine descent
   * instead of Java recursion.
   *
   * <p><b>@edgeCase</b> {@code childSpanIds} is keyed by each event's <em>own</em> recorded parent
   * span id, not deduplicated by span id first: two {@code EnterEvent}s that happen to share a span
   * id but disagree on their parent (a malformed or replayed stream, never a genuine
   * single-threaded capture) can register that id as a child under two different parents, and if
   * those parents are themselves each other's descendant the result is a cycle reachable from a
   * real root — nothing a flat, well-formed event stream could produce, but nothing before this
   * guarded against one that is not. {@code onPath} is a plain, value-equality {@link HashSet}:
   * unlike {@link TreeWalk}'s identity-based cycle guard (needed there because two distinct,
   * coincidentally-equal {@code TraceNode}s must not be confused), a {@link SpanId} is a value type
   * and two instances carrying the same id genuinely are the same span for this purpose.
   *
   * <p>A span id beyond {@link TreeWalk#MAX_DEPTH} or already on the current path still gets its
   * own node — built from its own enter/exit data, exactly as a leaf would — the walk simply never
   * descends into its children a second time.
   */
  private static TraceNode buildNodeRecursive(SpanId rootSpanId, EventIndex index) {
    Set<SpanId> onPath = new HashSet<>();
    Deque<BuildFrame> stack = new ArrayDeque<>();
    pushBuildFrame(rootSpanId, 0, index, onPath, stack);

    TraceNode result = null;
    while (!stack.isEmpty()) {
      var frame = stack.peek();
      if (!frame.children().hasNext()) {
        var finished = finishFrame(frame, index, stack, onPath);
        if (stack.isEmpty()) {
          result = finished;
        }
        continue;
      }
      descendToNextChild(frame, index, onPath, stack);
    }
    return result;
  }

  /** Completes the top frame's node and, unless it was the root, hands it to its parent frame. */
  private static TraceNode finishFrame(
      BuildFrame frame, EventIndex index, Deque<BuildFrame> stack, Set<SpanId> onPath) {
    onPath.remove(frame.spanId());
    var node = leafFor(frame.spanId(), index, frame.built());
    stack.pop();
    if (!stack.isEmpty()) {
      stack.peek().built().add(node);
    }
    return node;
  }

  /**
   * Either descends into the frame's next child, or — bound or cycle — contributes it as a leaf.
   */
  private static void descendToNextChild(
      BuildFrame frame, EventIndex index, Set<SpanId> onPath, Deque<BuildFrame> stack) {
    var childId = frame.children().next();
    int childDepth = frame.depth() + 1;
    if (onPath.contains(childId) || childDepth > TreeWalk.MAX_DEPTH) {
      frame.built().add(leafFor(childId, index, List.of()));
    } else {
      pushBuildFrame(childId, childDepth, index, onPath, stack);
    }
  }

  private static void pushBuildFrame(
      SpanId spanId, int depth, EventIndex index, Set<SpanId> onPath, Deque<BuildFrame> stack) {
    onPath.add(spanId);
    var children = index.childSpanIds.getOrDefault(spanId, List.<SpanId>of()).iterator();
    stack.push(new BuildFrame(spanId, depth, children, new ArrayList<>()));
  }

  private static TraceNode leafFor(SpanId spanId, EventIndex index, List<TraceNode> children) {
    return assembleNode(index.enters.get(spanId), index.exits.get(spanId), children);
  }

  private record BuildFrame(
      SpanId spanId, int depth, Iterator<SpanId> children, List<TraceNode> built) {}

  private static TraceNode assembleNode(
      TraceEvent.EnterEvent enter, TraceEvent.ExitEvent exit, List<TraceNode> children) {
    var outcome = exit != null ? exit.outcome() : new TraceOutcome.Incomplete();
    long duration = exit != null ? exit.timestampNanos() - enter.timestampNanos() : 0L;
    var sig = applyErrorContext(enter.signature(), exit);
    return new TraceNode(
        sig,
        List.copyOf(children),
        outcome,
        duration,
        enter.timestampNanos(),
        enter.concurrency(),
        enter.spanContext(),
        enter.thread());
  }

  /**
   * The signature with the exit's error context on it, or the signature itself when there is none.
   *
   * <p><b>@llmNote</b> The {@code exit.errorContext() == null} half of the guard is an
   * <em>equivalent</em> mutant and will always survive PIT: {@code withErrorContext(null)} on a
   * signature that already has none returns a record equal to the one passed in, so no assertion
   * over behaviour can tell the branch from its absence. The guard earns its place by not
   * allocating that copy, not by changing what is recorded.
   */
  private static MethodSignature applyErrorContext(MethodSignature sig, TraceEvent.ExitEvent exit) {
    if (exit == null || exit.errorContext() == null) {
      return sig;
    }
    return sig.withErrorContext(exit.errorContext());
  }

  private static ArrayList<TraceNode> retainErrorPaths(List<TraceNode> nodes) {
    var result = new ArrayList<TraceNode>();
    for (var node : nodes) {
      var pruned = retainErrorPathsNode(node);
      if (pruned != null) {
        result.add(pruned);
      }
    }
    return result;
  }

  /**
   * {@code ERRORS} pruning for one root: {@code null} when neither this node nor any descendant
   * failed, otherwise this node rebuilt with only the descendants that lead to a failure.
   *
   * <p><b>@edgeCase</b> Bounded and cycle-safe via {@link TreeWalk}, for a node this deep the same
   * reason {@link #buildNodeRecursive} itself now is: {@code MAX_DEPTH} (10 000) is far beyond what
   * plain call-stack recursion survives, so even the already-bounded output of construction could
   * overflow a naive recursive walk here. An error node's own subtree is kept whole and unmodified
   * — {@code childrenOf} stops there deliberately, matching the short-circuit the original
   * recursive form took — so only a non-error node's real children are ever walked. A node beyond
   * the bound is kept as-is, the same conservative choice a genuine error deep inside an
   * unreachable subtree would force anyway: cannot be proven innocent, so it is not silently
   * dropped.
   */
  private static TraceNode retainErrorPathsNode(TraceNode root) {
    Deque<List<TraceNode>> kept = new ArrayDeque<>();
    TraceNode[] result = {null};
    TreeWalk.walk(
        root,
        node -> isErrorOutcome(node) ? List.of() : node.children(),
        (node, depth) -> kept.push(new ArrayList<>()),
        (node, depth) -> addKept(kept, keptResultFor(node, kept.pop()), result),
        (node, depth, reason) -> addKept(kept, node, result));
    return result[0];
  }

  private static TraceNode keptResultFor(TraceNode node, List<TraceNode> children) {
    if (isErrorOutcome(node)) {
      return node;
    }
    if (children.isEmpty()) {
      return null;
    }
    return new TraceNode(
        node.signature(),
        List.copyOf(children),
        node.outcome(),
        node.durationNanos(),
        node.startTimeNanos(),
        node.concurrency(),
        node.spanContext());
  }

  private static void addKept(Deque<List<TraceNode>> kept, TraceNode built, TraceNode[] result) {
    if (built == null) {
      return;
    }
    if (kept.isEmpty()) {
      result[0] = built;
    } else {
      kept.peek().add(built);
    }
  }

  private static boolean isErrorOutcome(TraceNode node) {
    return node.outcome() instanceof TraceOutcome.Threw
        || node.outcome() instanceof TraceOutcome.Incomplete;
  }

  /**
   * {@code SUMMARY} pruning: every root keeps its place, and under it only the leaves and the
   * failures survive — the intermediate levels are lifted away.
   *
   * <p>INTENT: Share what pruning did not touch. A root whose children are already exactly the set
   * {@code SUMMARY} keeps is returned as <em>the same instance</em>, and so is the list holding it;
   * a capture pays an allocation only for the roots it actually rewrites. This is why {@code
   * SUMMARY} costs no more than {@code DETAIL} on a trace it does not prune.
   *
   * <p><b>@llmNote</b> Sharing is safe because {@link TraceNode} is an immutable record: no caller
   * can tell a shared node from a copied one except by {@code ==}, which carries no meaning here.
   * Package-private because that sharing contract is the unit under test.
   */
  static List<TraceNode> pruneSummary(List<TraceNode> roots) {
    List<TraceNode> rewritten = null;
    for (int i = 0; i < roots.size(); i++) {
      var pruned = pruneSummaryNode(roots.get(i));
      if (pruned == roots.get(i)) { // NOPMD - identity IS the question being asked
        continue;
      }
      if (rewritten == null) {
        rewritten = new ArrayList<>(roots);
      }
      rewritten.set(i, pruned);
    }
    return rewritten != null ? rewritten : roots;
  }

  private static TraceNode pruneSummaryNode(TraceNode node) {
    if (keepsEveryChild(node)) {
      return node;
    }
    var lifted = new ArrayList<TraceNode>();
    for (var child : node.children()) {
      pruneSummaryCollect(child, lifted);
    }
    return withChildren(node, lifted);
  }

  /**
   * Whether {@code SUMMARY} would keep this node's children exactly as they are, in this order —
   * which is the case when every one of them is a node {@link #pruneSummaryCollect} retains rather
   * than descends into. Answering this before collecting is what makes the sharing possible.
   */
  private static boolean keepsEveryChild(TraceNode node) {
    var children = node.children();
    for (int i = 0; i < children.size(); i++) {
      if (!isSummaryLeaf(children.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Appends every summary leaf under {@code root} (itself included, if it is one) to {@code
   * collector}, in visitation order.
   *
   * <p><b>@edgeCase</b> Bounded and cycle-safe via {@link TreeWalk} for the same reason {@link
   * #retainErrorPathsNode} is: {@code childrenOf} stops at a summary leaf deliberately (matching
   * the original recursion's short-circuit), so only a non-leaf's real children are ever walked. A
   * node beyond the bound is collected as if it were a leaf — the same "still contributes itself,
   * never descended into again" treatment {@link TreeWalk} gives every renderer.
   */
  private static void pruneSummaryCollect(TraceNode root, List<TraceNode> collector) {
    TreeWalk.walk(
        root,
        node -> isSummaryLeaf(node) ? List.of() : node.children(),
        (node, depth) -> {
          if (isSummaryLeaf(node)) {
            collector.add(node);
          }
        },
        (node, depth, reason) -> collector.add(node));
  }

  /** A node {@code SUMMARY} retains with its own subtree instead of lifting its descendants. */
  private static boolean isSummaryLeaf(TraceNode node) {
    return node.children().isEmpty() || isErrorOutcome(node);
  }

  /**
   * The node with a different child list and everything else intact — including the thread
   * identity, which a pruned node keeps for the same reason an unpruned one does: pruning decides
   * which calls are shown, never what is known about the calls that are.
   */
  private static TraceNode withChildren(TraceNode node, List<TraceNode> children) {
    return new TraceNode(
        node.signature(),
        List.copyOf(children),
        node.outcome(),
        node.durationNanos(),
        node.startTimeNanos(),
        node.concurrency(),
        node.spanContext(),
        node.thread());
  }

  private record EventIndex(
      Map<SpanId, TraceEvent.EnterEvent> enters,
      Map<SpanId, TraceEvent.ExitEvent> exits,
      Map<SpanId, List<SpanId>> childSpanIds,
      List<SpanId> rootSpanIds) {

    static EventIndex of(List<TraceEvent> events) {
      var enters = new HashMap<SpanId, TraceEvent.EnterEvent>();
      var exits = new HashMap<SpanId, TraceEvent.ExitEvent>();
      for (var event : events) {
        if (event instanceof TraceEvent.EnterEvent e) {
          enters.put(e.spanContext().spanId(), e);
        } else if (event instanceof TraceEvent.ExitEvent e) {
          exits.put(e.spanContext().spanId(), e);
        }
      }
      var childSpanIds = new HashMap<SpanId, List<SpanId>>();
      var rootSpanIds = new ArrayList<SpanId>();
      for (var event : events) {
        if (event instanceof TraceEvent.EnterEvent e) {
          SpanId sid = e.spanContext().spanId();
          SpanId parentSpanId = e.spanContext().parentSpanId();
          if (parentSpanId == null || !enters.containsKey(parentSpanId)) {
            rootSpanIds.add(sid);
          } else {
            childSpanIds.computeIfAbsent(parentSpanId, k -> new ArrayList<>()).add(sid);
          }
        }
      }
      return new EventIndex(enters, exits, childSpanIds, rootSpanIds);
    }
  }
}
