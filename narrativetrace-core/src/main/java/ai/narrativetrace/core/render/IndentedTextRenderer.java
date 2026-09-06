/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plain-text renderer using tree indentation and arrow notation.
 *
 * <p>INTENT: Use this for console output, failure messages, and quick inspection where Markdown
 * formatting would be noisy.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own single-line entry, with
 * {@link TreeWalk.Reason#marker()} appended; the walk simply never descends into its children.
 */
public final class IndentedTextRenderer implements NarrativeRenderer {

  /**
   * One node's rendering context: the prefix for its own line and for its continuation lines, plus
   * text to print immediately before it and immediately after its whole subtree — a segment header
   * or footer ({@code ⤳ fire-and-forget}, a whole self-contained {@code ⑂ fork}/{@code ⑃ join}
   * block, which never recurses into its members' children) that could not print immediately
   * because an earlier sibling had not finished rendering, or has no later sibling to attach to
   * instead. See {@link #planChildren}.
   */
  private record Prefix(String line, String cont, String leading, String trailing) {}

  /**
   * Mutable working form of {@link Prefix} while a sibling list is being planned; see {@link
   * #flush}.
   */
  private static final class Planned {
    final TraceNode node;
    final String line;
    final String cont;
    String leading;
    String trailing;

    Planned(TraceNode node, String line, String cont, String leading) {
      this.node = node;
      this.line = line;
      this.cont = cont;
      this.leading = leading;
    }
  }

  public IndentedTextRenderer() {}

  @Override
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    for (var root : tree.roots()) {
      renderTree(root, sb);
    }
    return sb.toString().stripTrailing() + LossFooter.block(tree, "");
  }

  /**
   * Walks one root, bounded and cycle-safe. {@code prefixOf} is the side channel that carries each
   * occurrence's line/continuation prefixes from the point they are computed ({@link
   * #planChildren}, run once per visited node as {@link TreeWalk}'s {@code childrenOf}) to the
   * point they are consumed ({@link #enterNode}/{@link #limitedNode}/{@link #exitNode}) — see
   * {@link NodeContext} for why a genuine cycle needs more than a plain identity map here.
   */
  private void renderTree(TraceNode root, StringBuilder sb) {
    var prefixOf = new NodeContext<TraceNode, Prefix>();
    prefixOf.push(root, new Prefix("", "", null, null));
    TreeWalk.walk(
        root,
        node -> planChildren(node, prefixOf, sb),
        (node, depth) -> enterNode(node, prefixOf, sb),
        (node, depth) -> exitNode(node, prefixOf, sb),
        (node, depth, reason) -> limitedNode(node, prefixOf, reason, sb));
  }

  /**
   * Computes {@code node}'s children for the walk, in the same pass planning the segment-level text
   * ({@code ~ fire-and-forget}, a whole self-contained {@code ⑂ fork}/{@code ⑃ join} block) that
   * has no node of its own — called exactly once per visited node.
   *
   * <p><b>@llmNote</b> {@link TreeWalk}'s {@code childrenOf} plans every segment of {@code node}'s
   * children in this one call, before any of them are actually walked, but the original recursion
   * printed segment text in visitation order — a fork that follows a plain sibling must print only
   * after that sibling's whole subtree is rendered. {@code carry} accumulates text that has no
   * walked node yet (a fork never recurses into its members here, and an empty fire-and-forget
   * produces no node at all); it becomes the next node's {@link Prefix#leading()} the moment one
   * exists, or is appended to the last planned node's line, or straight to {@code sb}, once the
   * whole sibling list is planned.
   */
  private List<TraceNode> planChildren(
      TraceNode node, NodeContext<TraceNode, Prefix> prefixOf, StringBuilder sb) {
    var contPrefix = prefixOf.peek(node).cont();
    var planned = new java.util.ArrayList<Planned>();
    var carry = new StringBuilder();
    for (var segment : ChildSegment.partition(node.children())) {
      if (segment.groupId == null) {
        var child = segment.nodes.get(0);
        planned.add(new Planned(child, contPrefix + "├── ", contPrefix + "│   ", flush(carry)));
      } else if (segment.isFireAndForget()) {
        planFireAndForget(segment.nodes.get(0), contPrefix, planned, carry);
      } else {
        renderConcurrentGroup(segment.nodes, contPrefix, carry);
      }
    }
    flushCarryToLast(planned, carry, sb);
    var result = new java.util.ArrayList<TraceNode>(planned.size());
    for (var p : planned) {
      prefixOf.push(p.node, new Prefix(p.line, p.cont, p.leading, p.trailing));
      result.add(p.node);
    }
    return result;
  }

  private void flushCarryToLast(List<Planned> planned, StringBuilder carry, StringBuilder sb) {
    if (carry.isEmpty()) {
      return;
    }
    if (planned.isEmpty()) {
      // Nothing walked in this sibling list at all (every segment was an empty fire-and-forget or a
      // fork) — nothing to attach to, and nothing else pending to interleave before it either.
      sb.append(carry);
      return;
    }
    var last = planned.get(planned.size() - 1);
    last.trailing = (last.trailing == null ? "" : last.trailing) + carry;
  }

  /** Consumes and returns {@code carry}'s text, or {@code null} when there is none to attach. */
  private String flush(StringBuilder carry) {
    if (carry.isEmpty()) {
      return null;
    }
    var text = carry.toString();
    carry.setLength(0);
    return text;
  }

  private void planFireAndForget(
      TraceNode launcher, String contPrefix, List<Planned> planned, StringBuilder carry) {
    carry.append(contPrefix).append("├── ⤳ fire-and-forget");
    if (launcher.concurrency() != null) {
      carry.append(" [thread: ").append(launcher.concurrency().threadName()).append("]");
    }
    carry.append("\n");
    if (launcher.children().isEmpty()) {
      carry.append(contPrefix).append("│       [launched, result not captured]\n");
      return;
    }
    var children = launcher.children();
    for (var idx = 0; idx < children.size(); idx++) {
      planned.add(
          new Planned(
              children.get(idx),
              contPrefix + "│   ├── ",
              contPrefix + "│   │   ",
              idx == 0 ? flush(carry) : null));
    }
  }

  private void enterNode(
      TraceNode node, NodeContext<TraceNode, Prefix> prefixOf, StringBuilder sb) {
    var prefix = prefixOf.peek(node);
    if (prefix.leading() != null) {
      sb.append(prefix.leading());
    }
    var sig = node.signature();
    var params = sig.parameters().stream().map(this::renderParam).collect(Collectors.joining(", "));
    var header = signatureText(sig) + "(" + params + ")";

    if (node.children().isEmpty()) {
      sb.append(prefix.line()).append(header);
      renderOutcomeInline(node.outcome(), sig, sb);
      renderDuration(node, sb);
      sb.append("\n");
      renderNarration(sig, prefix.cont(), sb);
    } else {
      sb.append(prefix.line()).append(header).append("\n");
      renderNarration(sig, prefix.cont(), sb);
    }
  }

  private void exitNode(TraceNode node, NodeContext<TraceNode, Prefix> prefixOf, StringBuilder sb) {
    var prefix = prefixOf.pop(node);
    if (!node.children().isEmpty()) {
      sb.append(prefix.cont()).append("└── ");
      renderOutcomeClosing(node.outcome(), node.signature(), sb);
      renderDuration(node, sb);
      sb.append("\n");
    }
    if (prefix.trailing() != null) {
      sb.append(prefix.trailing());
    }
  }

  /** A node the walk stopped at: rendered as a leaf (one line), plus the marker. */
  private void limitedNode(
      TraceNode node,
      NodeContext<TraceNode, Prefix> prefixOf,
      TreeWalk.Reason reason,
      StringBuilder sb) {
    var prefix = prefixOf.pop(node);
    var sig = node.signature();
    var params = sig.parameters().stream().map(this::renderParam).collect(Collectors.joining(", "));
    sb.append(prefix.line()).append(signatureText(sig)).append("(").append(params).append(")");
    renderOutcomeInline(node.outcome(), sig, sb);
    renderDuration(node, sb);
    sb.append(" ").append(reason.marker()).append("\n");
    renderNarration(sig, prefix.cont(), sb);
    // TreeWalk never calls onExit for a limited node, so trailing text — normally appended there —
    // is appended here instead; a limited node is treated as a leaf either way.
    if (prefix.trailing() != null) {
      sb.append(prefix.trailing());
    }
  }

  /**
   * The {@code Class.method} prefix, with control characters rendered inert.
   *
   * <p>INTENT: {@code MethodSignature} is a public record whose fields are unvalidated, and trace
   * trees are also built by hand, deserialized and post-processed. This renderer is line-oriented —
   * one node is one line — so a raw newline in a class or method name forges an entry a reader or a
   * log parser cannot distinguish from a real one (CWE-117), and a raw ESC injects an ANSI sequence
   * into the console this renderer exists to write to. Exception <em>messages</em> were already
   * sanitized here; the names beside them were not.
   */
  private String signatureText(MethodSignature sig) {
    return ControlEscape.sanitize(sig.className()) + "." + ControlEscape.sanitize(sig.methodName());
  }

  private void renderConcurrentGroup(List<TraceNode> members, String contPrefix, StringBuilder sb) {
    var analysis = SequentialAsyncDetector.analyze(members);
    sb.append(contPrefix).append("├── ⑂ fork [").append(members.size()).append(" tasks]\n");
    var sorted = members.stream().sorted(Comparator.comparing(this::sigKey)).toList();
    for (var member : sorted) {
      renderConcurrentMember(member, contPrefix + "│   ", analysis.isSequentialAsync(), sb);
    }
    long wallMs = members.stream().mapToLong(TraceNode::durationNanos).max().orElse(0) / 1_000_000;
    sb.append(contPrefix).append("├── ⑃ join — ").append(wallMs).append("ms\n");
    if (analysis.isSequentialAsync()) {
      appendSequentialAsyncHint(analysis, contPrefix, sb);
    }
  }

  private void renderConcurrentMember(
      TraceNode node, String contPrefix, boolean sequentialAsync, StringBuilder sb) {
    var sig = node.signature();
    var params = sig.parameters().stream().map(this::renderParam).collect(Collectors.joining(", "));
    var header = signatureText(sig) + "(" + params + ")";
    sb.append(contPrefix).append("├── ↦ ").append(header);
    renderOutcomeInline(node.outcome(), sig, sb);
    renderDuration(node, sb);
    sb.append("\n");
    if (node.concurrency() != null) {
      sb.append(contPrefix).append("│       [thread: ").append(node.concurrency().threadName());
      if (sequentialAsync) {
        sb.append("] [async, awaited sequentially]\n");
      } else {
        sb.append("]\n");
      }
    }
  }

  private void appendSequentialAsyncHint(
      SequentialAsyncResult analysis, String contPrefix, StringBuilder sb) {
    sb.append(contPrefix)
        .append("├── ⚡ Sequential async: total ")
        .append(analysis.totalMillis())
        .append("ms, parallelizable to ~")
        .append(analysis.parallelizableMillis())
        .append("ms\n");
  }

  private String sigKey(TraceNode node) {
    return signatureText(node.signature());
  }

  private void renderOutcomeInline(TraceOutcome outcome, MethodSignature sig, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r) {
      if (r.renderedValue() != null) {
        sb.append(" → ").append(r.renderedValue());
      }
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append(" !! ")
          .append(ControlEscape.sanitize(t.exception().getClass().getSimpleName()))
          .append(": ")
          .append(ExceptionMessage.text(t.exception()));
      if (sig.errorContext() != null) {
        sb.append(" | ").append(ControlEscape.sanitize(sig.errorContext()));
      }
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(" ⏳ in-flight");
    }
  }

  private void renderOutcomeClosing(TraceOutcome outcome, MethodSignature sig, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r) {
      if (r.renderedValue() != null) {
        sb.append("→ ").append(r.renderedValue());
      }
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append("!! ")
          .append(ControlEscape.sanitize(t.exception().getClass().getSimpleName()))
          .append(": ")
          .append(ExceptionMessage.text(t.exception()));
      if (sig.errorContext() != null) {
        sb.append(" | ").append(ControlEscape.sanitize(sig.errorContext()));
      }
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append("⏳ in-flight");
    }
  }

  private void renderNarration(MethodSignature sig, String contPrefix, StringBuilder sb) {
    if (sig.narration() != null) {
      sb.append(contPrefix)
          .append("│   // ")
          .append(ControlEscape.sanitize(sig.narration()))
          .append("\n");
    }
  }

  private void renderDuration(TraceNode node, StringBuilder sb) {
    if (node.durationNanos() > 0) {
      long millis = node.durationMillis();
      sb.append(" — ").append(millis).append("ms");
    }
  }

  private String renderParam(ParameterCapture param) {
    if (param.redacted()) {
      return ControlEscape.sanitize(param.name()) + ": [REDACTED]";
    }
    return ControlEscape.sanitize(param.name()) + ": " + param.renderedValue();
  }
}
