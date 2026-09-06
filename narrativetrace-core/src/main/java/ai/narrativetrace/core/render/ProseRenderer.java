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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renderer that turns traces into short prose paragraphs.
 *
 * <p>INTENT: Use this when you want the trace to read more like a narrative summary than a call
 * tree.
 *
 * <p><b>@llmNote</b> Every text field this renderer prints that did not come through {@code
 * ValueRenderer} — class and method names, parameter names, narration, error context, the
 * exception's type and its message — is folded through {@link ControlEscape} first, exactly as
 * {@link IndentedTextRenderer} folds them. The format is lines; a raw line break in any of those
 * fields forges one.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own sentence, closed inline,
 * with {@link TreeWalk.Reason#marker()} appended; the walk simply never descends into its children.
 */
public final class ProseRenderer implements NarrativeRenderer {

  /**
   * One node's rendering context: its indent level — distinct from {@link TreeWalk}'s own walk
   * depth, because a fire-and-forget launcher or a concurrency group occupies a prose paragraph of
   * its own without being a walked node, so its members render one indent level deeper than a plain
   * child would at the same walk depth — plus text to print immediately before it and immediately
   * after its whole subtree. See {@link #planChildren}.
   */
  private record Ctx(int depth, String leading, String trailing) {}

  /**
   * Mutable working form of {@link Ctx} while a sibling list is being planned; see {@link #flush}.
   */
  private static final class Planned {
    final TraceNode node;
    final int depth;
    String leading;
    String trailing;

    Planned(TraceNode node, int depth, String leading) {
      this.node = node;
      this.depth = depth;
      this.leading = leading;
    }
  }

  public ProseRenderer() {}

  @Override
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    for (var root : tree.roots()) {
      renderTree(root, sb);
    }
    return sb.toString().stripTrailing() + LossFooter.block(tree, "");
  }

  /**
   * Walks one root, bounded and cycle-safe. See {@link NodeContext} for why a genuine cycle needs
   * more than a plain identity map here.
   */
  private void renderTree(TraceNode root, StringBuilder sb) {
    var ctxOf = new NodeContext<TraceNode, Ctx>();
    ctxOf.push(root, new Ctx(0, null, null));
    TreeWalk.walk(
        root,
        node -> planChildren(node, ctxOf, sb),
        (node, depth) -> enterNode(node, ctxOf, sb),
        (node, depth) -> exitNode(node, ctxOf, sb),
        (node, depth, reason) -> limitedNode(node, ctxOf, reason, sb));
  }

  /**
   * Computes {@code node}'s children for the walk, in the same pass planning the paragraph text
   * ({@code In the background:}, {@code Concurrently:} and its trailing hint) that has no node of
   * its own — called exactly once per visited node.
   *
   * <p><b>@llmNote</b> {@link TreeWalk}'s {@code childrenOf} plans every segment of {@code node}'s
   * children in this one call, before any of them are actually walked, but the original recursion
   * printed segment text in visitation order — a concurrency paragraph that follows a plain sibling
   * must print only after that sibling's whole subtree is rendered. {@code carry} accumulates text
   * that has no walked node yet (an empty fire-and-forget produces none at all); it becomes the
   * next node's {@link Ctx#leading()} the moment one exists, or is attached to the last planned
   * node's {@link Ctx#trailing()}, or goes straight to {@code sb} when the whole sibling list
   * produced no walked node at all.
   */
  private List<TraceNode> planChildren(
      TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var depth = ctxOf.peek(node).depth() + 1;
    var planned = new ArrayList<Planned>();
    var carry = new StringBuilder();
    for (var segment : ChildSegment.partition(node.children())) {
      if (segment.groupId == null) {
        planned.add(new Planned(segment.nodes.get(0), depth, flush(carry)));
      } else if (segment.isFireAndForget()) {
        planFireAndForget(segment.nodes.get(0), depth, planned, carry);
      } else {
        planConcurrentGroup(segment.nodes, depth, planned, carry);
      }
    }
    flushCarryToLast(planned, carry, sb);
    var result = new ArrayList<TraceNode>(planned.size());
    for (var p : planned) {
      ctxOf.push(p.node, new Ctx(p.depth, p.leading, p.trailing));
      result.add(p.node);
    }
    return result;
  }

  private void flushCarryToLast(List<Planned> planned, StringBuilder carry, StringBuilder sb) {
    if (carry.isEmpty()) {
      return;
    }
    if (planned.isEmpty()) {
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
      TraceNode launcher, int depth, List<Planned> planned, StringBuilder carry) {
    var indent = "  ".repeat(depth);
    carry.append(indent).append("In the background:\n");
    if (launcher.children().isEmpty()) {
      carry.append(indent).append("  (launched, result not captured).\n");
      return;
    }
    var children = launcher.children();
    for (var idx = 0; idx < children.size(); idx++) {
      planned.add(new Planned(children.get(idx), depth + 1, idx == 0 ? flush(carry) : null));
    }
  }

  private void planConcurrentGroup(
      List<TraceNode> members, int depth, List<Planned> planned, StringBuilder carry) {
    var indent = "  ".repeat(depth);
    var analysis = SequentialAsyncDetector.analyze(members);
    carry.append(indent).append("Concurrently:\n");
    var sorted = members.stream().sorted(Comparator.comparing(this::sigKey)).toList();
    var hint =
        analysis.isSequentialAsync()
            ? indent
                + "  (Note: tasks ran sequentially — total "
                + analysis.totalMillis()
                + "ms, parallelizable to ~"
                + analysis.parallelizableMillis()
                + "ms.)\n"
            : "";
    for (var i = 0; i < sorted.size(); i++) {
      var p = new Planned(sorted.get(i), depth + 1, i == 0 ? flush(carry) : null);
      if (i == sorted.size() - 1 && !hint.isEmpty()) {
        p.trailing = hint;
      }
      planned.add(p);
    }
  }

  private void enterNode(TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var ctx = ctxOf.peek(node);
    if (ctx.leading() != null) {
      sb.append(ctx.leading());
    }
    var indent = "  ".repeat(ctx.depth());
    appendActionPhrase(sb, indent, node);
    if (node.children().isEmpty()) {
      renderOutcomeInline(node.outcome(), node.signature(), sb);
      sb.append(".\n");
    } else {
      sb.append(":\n");
    }
  }

  private void exitNode(TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (!node.children().isEmpty()) {
      renderOutcomeClosing(node.outcome(), "  ".repeat(ctx.depth()), sb);
    }
    if (ctx.trailing() != null) {
      sb.append(ctx.trailing());
    }
  }

  /** A node the walk stopped at: rendered as a leaf sentence, plus the marker. */
  private void limitedNode(
      TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, TreeWalk.Reason reason, StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (ctx.leading() != null) {
      sb.append(ctx.leading());
    }
    var indent = "  ".repeat(ctx.depth());
    appendActionPhrase(sb, indent, node);
    renderOutcomeInline(node.outcome(), node.signature(), sb);
    sb.append(" ").append(reason.marker()).append(".\n");
    // TreeWalk never calls onExit for a limited node, so trailing text — normally appended there —
    // is appended here instead; a limited node is treated as a leaf either way.
    if (ctx.trailing() != null) {
      sb.append(ctx.trailing());
    }
  }

  private String sigKey(TraceNode node) {
    return node.signature().className() + "." + node.signature().methodName();
  }

  private void appendActionPhrase(StringBuilder sb, String indent, TraceNode node) {
    var sig = node.signature();
    var subject = "The " + phrase(sig.className());
    var action = phrase(sig.methodName());

    sb.append(indent).append(subject).append(" ");

    boolean isError =
        node.outcome() instanceof TraceOutcome.Threw
            || node.outcome() instanceof TraceOutcome.Incomplete;
    if (isError) {
      sb.append("failed to ");
    }
    sb.append(action);

    if (sig.narration() != null && !isError) {
      sb.append(" — ").append(ControlEscape.sanitize(sig.narration()));
    } else {
      var params = renderParams(node);
      if (!params.isEmpty()) {
        sb.append(" for ").append(params);
      }
    }
  }

  /**
   * A name turned into readable words, with control characters rendered inert.
   *
   * <p>INTENT: This format's whole structure is lines — one sentence per node — so a raw newline
   * anywhere in it forges a sentence a reader cannot tell from a real one (CWE-117), and a raw ESC
   * injects an ANSI sequence into the console. {@code MethodSignature} is a public record with no
   * validation, and a tree is also built by hand, deserialized or post-processed, so every one of
   * its text fields is attacker-reachable. {@link IndentedTextRenderer#signatureText} has folded
   * its names through {@link ControlEscape} since the same audit; this renderer folded nothing at
   * all until 2026-09-04, which is why its siblings' oracle could not be pointed at it.
   */
  private static String phrase(String name) {
    return ControlEscape.sanitize(CamelCaseSplitter.toPhrase(name));
  }

  private void renderOutcomeClosing(TraceOutcome outcome, String indent, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r && r.renderedValue() != null) {
      sb.append(indent).append("  Returned ").append(r.renderedValue()).append(".\n");
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append(indent)
          .append("  ")
          .append(ControlEscape.sanitize(t.exception().getClass().getSimpleName()))
          .append(": ")
          .append(ExceptionMessage.text(t.exception()))
          .append(".\n");
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(indent).append("  ⏳ in-flight.\n");
    }
  }

  private void renderOutcomeInline(TraceOutcome outcome, MethodSignature sig, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r && r.renderedValue() != null) {
      sb.append(", returning ").append(r.renderedValue());
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append(" — ")
          .append(ControlEscape.sanitize(t.exception().getClass().getSimpleName()))
          .append(": ")
          .append(ExceptionMessage.text(t.exception()));
      if (sig.errorContext() != null) {
        sb.append(" (").append(ControlEscape.sanitize(sig.errorContext())).append(")");
      }
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(" — ⏳ in-flight");
    }
  }

  private String renderParams(TraceNode node) {
    var params = node.signature().parameters();
    if (params.isEmpty()) {
      return "";
    }
    return params.stream().map(this::renderParam).collect(Collectors.joining(" "));
  }

  private String renderParam(ParameterCapture param) {
    if (param.redacted()) {
      return ControlEscape.sanitize(param.name()) + ": [REDACTED]";
    }
    return ControlEscape.sanitize(param.name()) + ": " + param.renderedValue();
  }
}
