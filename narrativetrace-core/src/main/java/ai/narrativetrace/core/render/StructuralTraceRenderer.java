/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static ai.narrativetrace.core.render.SiblingCarry.flush;
import static ai.narrativetrace.core.render.SiblingCarry.flushToLast;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the AI-safe structural trace artifact (ADR-002): the developer-authored shape of a
 * scenario with zero runtime values.
 *
 * <p>INTENT: One artifact per test scenario ({@code .nt}) containing only code structure — class,
 * method, and parameter <em>names</em>, the call hierarchy, and outcome <em>kinds</em>. No argument
 * or return values, no exception messages, no durations, no timestamps, no trace identifiers. Zero
 * runtime values means zero prompt-injection surface and zero PII, and the output is deterministic
 * byte-for-byte for identical behavior — the property that makes it the approved-trace baseline
 * ({@code .approved.nt}) and the cross-platform conformance-fixture format.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own line, with {@link
 * TreeWalk.Reason#marker()} appended; the walk simply never descends into its children.
 */
public final class StructuralTraceRenderer implements NarrativeRenderer {

  /**
   * One node's rendering context: its indent depth, its citable {@link SpanId} ({@code null} for
   * the root of a {@link #subtreeKey} walk, which prints none), plus text to print before and after
   * it.
   */
  private record Ctx(int depth, String id, String leading, String trailing) {}

  /**
   * Mutable working form of {@link Ctx} while a sibling list is being planned; see {@link
   * SiblingCarry}.
   */
  private static final class Planned extends PlannedSibling {
    final int depth;
    final String id;

    Planned(TraceNode node, int depth, String id, String leading) {
      super(node, leading);
      this.depth = depth;
      this.id = id;
    }
  }

  /**
   * Full artifact form: a {@code scenario:} header (the humanized test name — stable across runs)
   * followed by the structural call flow. Nothing else: no result, no ids, no dates, so the file
   * changes only when behavior changes.
   */
  public String renderDocument(TraceTree tree, String scenario) {
    return "scenario: " + ControlEscape.sanitize(scenario) + "\n\n" + render(tree);
  }

  @Override
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    var ctxOf = new NodeContext<TraceNode, Ctx>();
    // Roots go through the same partitioning as children: async work that outlived its caller is a
    // root, and its order is the scheduler's, not the code's.
    for (var root : planChildren(tree.roots(), 0, null, ctxOf, sb)) {
      walkFrom(root, ctxOf, sb);
    }
    return sb.toString();
  }

  /**
   * Value-free structural key for a single subtree — the sameness oracle loop folding ({@code
   * MarkdownRenderer}) shares so there is exactly one definition of "same shape": equal signature
   * sequence, equal child shape recursively, and equal outcome kind at every node. Two sibling
   * subtrees fold together iff their keys are equal.
   *
   * <p><b>@llmNote</b> Reuses the same {@code enterNode} that produces the {@code .nt} artifact, so
   * folding sameness can never drift from the structural projection; it is deliberately package
   * private (a rendering-internal comparison, not an artifact).
   */
  String subtreeKey(TraceNode node) {
    var sb = new StringBuilder();
    var ctxOf = new NodeContext<TraceNode, Ctx>();
    ctxOf.push(node, new Ctx(0, null, null, null));
    walkFrom(node, ctxOf, sb);
    return sb.toString();
  }

  private void walkFrom(TraceNode root, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    TreeWalk.walk(
        root,
        node -> planChildren(node, ctxOf, sb),
        (node, depth) -> enterNode(node, ctxOf, sb),
        (node, depth) -> exitNode(node, ctxOf, sb),
        (node, depth, reason) -> limitedNode(node, ctxOf, reason, sb));
  }

  private void enterNode(TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var ctx = ctxOf.peek(node);
    if (ctx.leading() != null) {
      sb.append(ctx.leading());
    }
    appendLine(node, ctx.depth(), ctx.id(), null, sb);
  }

  private void exitNode(TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (ctx.trailing() != null) {
      sb.append(ctx.trailing());
    }
  }

  /** A node the walk stopped at: rendered as a leaf, plus the marker. */
  private void limitedNode(
      TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, TreeWalk.Reason reason, StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (ctx.leading() != null) {
      sb.append(ctx.leading());
    }
    appendLine(node, ctx.depth(), ctx.id(), reason.marker(), sb);
    // TreeWalk never calls onExit for a limited node, so trailing text — normally appended there —
    // is appended here instead; a limited node is treated as a leaf either way.
    if (ctx.trailing() != null) {
      sb.append(ctx.trailing());
    }
  }

  private void appendLine(TraceNode node, int depth, String id, String marker, StringBuilder sb) {
    var sig = node.signature();
    var params =
        sig.parameters().stream()
            .map(p -> ControlEscape.sanitize(p.name()))
            .collect(Collectors.joining(", "));
    sb.append("  ".repeat(depth));
    if (id != null) {
      sb.append(id).append(' ');
    }
    sb.append("- ")
        .append(ControlEscape.sanitize(sig.className()))
        .append(".")
        .append(ControlEscape.sanitize(sig.methodName()))
        .append("(")
        .append(params)
        .append(")");
    renderOutcomeKind(node.outcome(), sb);
    if (marker != null) {
      sb.append(" ").append(marker);
    }
    sb.append("\n");
  }

  private List<TraceNode> planChildren(
      TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var ctx = ctxOf.peek(node);
    return planChildren(node.children(), ctx.depth() + 1, ctx.id(), ctxOf, sb);
  }

  /**
   * Concurrent groups render under a marker ({@code ~ fork [n]}, {@code ~ async [n]}) with members
   * sorted by signature — capture order across threads is the scheduler's choice, not behaviour,
   * and this artifact must be byte-identical for identical behavior. Thread identity is runtime
   * data and never appears.
   *
   * <p><b>@llmNote</b> {@link TreeWalk}'s {@code childrenOf} plans every segment of {@code
   * children} in this one call, before any of them are actually walked, but the original recursion
   * printed segment text in visitation order — a group that follows a plain sibling must print only
   * after that sibling's whole subtree is rendered. {@code carry} accumulates text that has no
   * walked node yet (an empty fire-and-forget produces none at all); it becomes the next node's
   * {@link Ctx#leading()} the moment one exists, or is attached to the last planned node's {@link
   * Ctx#trailing()}, or goes straight to {@code sb} when the whole sibling list produced no walked
   * node at all.
   */
  private List<TraceNode> planChildren(
      List<TraceNode> children,
      int depth,
      String parentId,
      NodeContext<TraceNode, Ctx> ctxOf,
      StringBuilder sb) {
    var planned = new ArrayList<Planned>();
    var carry = new StringBuilder();
    var ids = new SpanCursor(parentId);
    for (var segment : ChildSegment.partition(children)) {
      planSegment(segment, depth, ids, planned, carry);
    }
    flushToLast(planned, carry, sb);
    var result = new ArrayList<TraceNode>(planned.size());
    for (var p : planned) {
      ctxOf.push(p.node, new Ctx(p.depth, p.id, p.leading, p.trailing));
      result.add(p.node);
    }
    return result;
  }

  /** Plans one segment, taking its spans' ids from {@code ids} in the order it lays them out. */
  private void planSegment(
      ChildSegment segment, int depth, SpanCursor ids, List<Planned> planned, StringBuilder carry) {
    var first = segment.nodes.get(0);
    if (segment.groupId == null) {
      planned.add(new Planned(first, depth, ids.next(), flush(carry)));
    } else if (segment.isFireAndForget()) {
      planFireAndForget(first, depth, ids.next(), planned, carry);
    } else {
      var marker = segment.isAsync() ? "~ async" : "~ fork";
      planGroup(marker, segment.nodes, depth, ids, planned, carry);
    }
  }

  private void planGroup(
      String marker,
      List<TraceNode> members,
      int depth,
      SpanCursor ids,
      List<Planned> planned,
      StringBuilder carry) {
    carry
        .append("  ".repeat(depth))
        .append(marker)
        .append(" [")
        .append(members.size())
        .append("]\n");
    var sorted = members.stream().sorted(SpanId.CONCURRENT_ORDER).toList();
    for (var i = 0; i < sorted.size(); i++) {
      planned.add(new Planned(sorted.get(i), depth + 1, ids.next(), i == 0 ? flush(carry) : null));
    }
  }

  private void planFireAndForget(
      TraceNode launcher,
      int depth,
      String launcherId,
      List<Planned> planned,
      StringBuilder carry) {
    carry.append("  ".repeat(depth)).append(launcherId).append(" ~ fire-and-forget\n");
    var launched = new SpanCursor(launcherId);
    for (var segment : ChildSegment.partition(launcher.children())) {
      planSegment(segment, depth + 1, launched, planned, carry);
    }
  }

  private void renderOutcomeKind(TraceOutcome outcome, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r && r.renderedValue() != null) {
      sb.append(" → value");
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append(" !! ").append(t.exception().getClass().getSimpleName());
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(" ?? incomplete");
    }
  }
}
