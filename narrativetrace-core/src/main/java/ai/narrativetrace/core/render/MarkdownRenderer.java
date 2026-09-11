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
 * Markdown renderer with optional document wrapper.
 *
 * <p>INTENT: Use this for human-readable trace files. It adds headings, nested call lists, timing,
 * narration, and concurrency annotations, and can wrap the result in YAML frontmatter for file
 * output.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own list item, with {@link
 * TreeWalk.Reason#marker()} appended; the walk simply never descends into its children.
 */
public final class MarkdownRenderer implements NarrativeRenderer {

  /** Milliseconds beyond which a call is marked slow, when nothing says otherwise. */
  private static final long DEFAULT_SLOW_THRESHOLD_MS = 200;

  private final long slowThresholdMs;
  private final boolean foldLoops;

  public MarkdownRenderer() {
    this(DEFAULT_SLOW_THRESHOLD_MS);
  }

  public MarkdownRenderer(long slowThresholdMs) {
    this(slowThresholdMs, true);
  }

  /**
   * @param foldLoops whether a run of same-shape consecutive siblings collapses into one {@code ×k
   *     more …} line; {@code false} renders every iteration in full — see {@link #unfolded()}
   */
  public MarkdownRenderer(long slowThresholdMs, boolean foldLoops) {
    this.slowThresholdMs = slowThresholdMs;
    this.foldLoops = foldLoops;
  }

  /**
   * The unfolded renderer: every iteration of a loop rendered in full, none summarized.
   *
   * <p>INTENT: Folding compresses sameness that has been <em>structurally</em> proven — the folded
   * iterations' values still differ, and the fold line can name only the first distinguishing
   * argument of each. When the question is "what did iteration five actually pass", that is not
   * enough, and a reader should not have to leave Markdown for the JSON artifact to answer it
   * (2026-09-08 agent evaluation).
   *
   * <p><b>@llmNote</b> Reached from a test run with {@code narrativetrace.unfolded=true}. Nothing
   * else about the document changes, so a folded and an unfolded render of one trace differ only in
   * the iterations the folded one summarizes.
   */
  public static MarkdownRenderer unfolded() {
    return new MarkdownRenderer(DEFAULT_SLOW_THRESHOLD_MS, false);
  }

  private static final StructuralTraceRenderer STRUCTURE = new StructuralTraceRenderer();

  /**
   * One node's rendering context: its indent depth, the bullet prefix that names its role ({@code
   * "- "} for a plain call, {@code "- ↦ "} for a fork member), text to print <em>before</em> the
   * node's own line — a segment header (fork, fire-and-forget) that could not print immediately
   * because an earlier sibling had not rendered yet — and text to print <em>after</em> its whole
   * subtree, including its own closing outcome line: a fork member's thread-info line (every
   * member) plus the group's {@code join} line (the last member only), or {@code null}.
   *
   * <p><b>@llmNote</b> A fold run's {@code ×k more …} summary is <em>not</em> here: {@link
   * LoopFold#summaryLine} mints reference labels through {@code refs} as a side effect, in
   * first-appearance order, so it must run exactly when the original recursion would have called it
   * — after the run's first iteration is fully rendered — never earlier. {@link #foldedSiblings}
   * carries the folded nodes so {@link #exitNode} can call it lazily, at that exact point, instead
   * of a plan-time string.
   */
  private record Ctx(
      int depth,
      String prefix,
      String leadingLine,
      String trailingLine,
      List<TraceNode> foldedSiblings) {}

  /**
   * Mutable working form of {@link Ctx} while a sibling list is being planned; see {@link #flush}.
   */
  private static final class Planned {
    final TraceNode node;
    final int depth;
    final String prefix;
    String leading;
    String trailing;
    List<TraceNode> foldedSiblings;

    Planned(TraceNode node, int depth, String prefix, String leading) {
      this.node = node;
      this.depth = depth;
      this.prefix = prefix;
      this.leading = leading;
    }
  }

  @Override
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    var refs = ValueReferenceIndex.build(tree);
    renderTree(tree.roots(), sb, refs);
    return sb.toString().stripTrailing() + LossFooter.block(tree, "> ");
  }

  public String renderDocument(TraceTree tree, TraceMetadata metadata) {
    var sb = new StringBuilder();
    sb.append(new FrontmatterBuilder().scenario(metadata.scenario()).build(tree));
    renderDocumentHeader(tree, metadata, sb);
    var refs = ValueReferenceIndex.build(tree);
    renderTree(tree.roots(), sb, refs);
    return sb.toString().stripTrailing() + LossFooter.block(tree, "> ");
  }

  /**
   * The {@code Class.method} text of a signature, escaped for Markdown prose.
   *
   * <p>INTENT: {@code MethodSignature} is a public record with unvalidated fields, and trace trees
   * are built by public API, deserialized from JSON and post-processed by integrations as well as
   * produced by the proxy. Every interpolation site here is a single line — a heading, a list item
   * — so a raw newline in a name is document structure, and the rendered Markdown is commonly
   * viewed in a browser, where a raw {@code <img onerror=...>} in a name is active HTML. Exception
   * messages already went through {@link MarkdownEscape#text} for exactly these reasons; the names
   * beside them did not.
   *
   * <p><b>@llmNote</b> One helper for both headings, so the document heading and the call heading
   * cannot drift on how much they escape.
   */
  private String signatureText(MethodSignature sig) {
    return MarkdownEscape.text(sig.className()) + "." + MarkdownEscape.text(sig.methodName());
  }

  /**
   * <b>@edgeCase</b> The scenario is caller-supplied text and the frontmatter already escapes it
   * (via {@code FrontmatterBuilder.yamlSafe}); this header escapes the same value for the Markdown
   * body — one escaping decision per sink, never a raw append. A raw scenario here forged document
   * structure (a heading of its own) and injected active HTML (2026-09-08 audit). The result's
   * {@code displayName()} stays raw on purpose: it is enum-controlled, never caller-supplied.
   */
  private void renderDocumentHeader(TraceTree tree, TraceMetadata metadata, StringBuilder sb) {
    if (tree.roots().isEmpty()) {
      return;
    }
    var sig = tree.roots().get(0).signature();
    var durationMs = DurationFormat.millis(tree.durationNanos());
    sb.append("\n## Trace: ").append(signatureText(sig)).append("\n\n");
    sb.append("**Scenario:** ").append(MarkdownEscape.text(metadata.scenario())).append("\n");
    sb.append("**Duration:** ")
        .append(durationMs)
        .append("ms | **Result:** ")
        .append(metadata.result().displayName())
        .append("\n\n");
    sb.append("### Call Flow\n\n");
  }

  /**
   * Walks every root, bounded and cycle-safe: roots go through the same segment planning as any
   * node's children (async work that outlived its caller is a root, and a loop can start at the top
   * level too), then each resulting position is walked with {@link TreeWalk}.
   */
  private void renderTree(List<TraceNode> roots, StringBuilder sb, ValueReferenceIndex refs) {
    var ctxOf = new NodeContext<TraceNode, Ctx>();
    for (var root : planSiblings(roots, 0, ctxOf, sb)) {
      TreeWalk.walk(
          root,
          node -> planChildren(node, ctxOf, sb),
          (node, depth) -> enterNode(node, ctxOf, refs, sb),
          (node, depth) -> exitNode(node, ctxOf, refs, sb),
          (node, depth, reason) -> limitedNode(node, ctxOf, refs, reason, sb));
    }
  }

  private List<TraceNode> planChildren(
      TraceNode node, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    return planSiblings(node.children(), ctxOf.peek(node).depth() + 1, ctxOf, sb);
  }

  /**
   * Plans a sibling list (tree roots or a node's children), folding maximal runs of ≥2 consecutive
   * same-shape sequential subtrees into one {@code ×k more …} line. Concurrency segments (fork
   * groups, fire-and-forget) render through their existing paths and never fold — they are not
   * sequential iterations.
   *
   * <p><b>@llmNote</b> A segment's own header text (fork, fire-and-forget) cannot always print
   * immediately: {@link TreeWalk}'s {@code childrenOf} plans <em>every</em> segment of a sibling
   * list in one call, before any of them are actually walked, but the original recursion printed
   * segment headers in visitation order — a fork that follows a plain sibling must print its header
   * only after that sibling's whole subtree is rendered. {@code carry} accumulates header text that
   * has no walked node yet (an empty fire-and-forget produces none at all); it is handed to the
   * next node's {@link Ctx#leadingLine()} the moment one exists, or — if the sibling list ends with
   * nothing left to attach it to — appended to the last planned node's trailing text, or straight
   * to {@code sb} when the whole list produced no walked node at all.
   */
  private List<TraceNode> planSiblings(
      List<TraceNode> siblings, int depth, NodeContext<TraceNode, Ctx> ctxOf, StringBuilder sb) {
    var segments = ChildSegment.partition(siblings);
    var planned = new ArrayList<Planned>();
    var carry = new StringBuilder();
    var i = 0;
    while (i < segments.size()) {
      i = planSegmentFrom(segments, i, depth, planned, carry);
    }
    flushCarryToLast(planned, carry, sb);
    var result = new ArrayList<TraceNode>(planned.size());
    for (var p : planned) {
      ctxOf.push(p.node, new Ctx(p.depth, p.prefix, p.leading, p.trailing, p.foldedSiblings));
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

  private int planSegmentFrom(
      List<ChildSegment> segments, int i, int depth, List<Planned> planned, StringBuilder carry) {
    var segment = segments.get(i);
    if (segment.groupId != null) {
      if (segment.isFireAndForget()) {
        planFireAndForget(segment.nodes.get(0), depth, planned, carry);
      } else {
        planConcurrentGroup(segment.nodes, depth, planned, carry);
      }
      return i + 1;
    }
    var end = foldLoops ? foldRunEnd(segments, i) : i + 1;
    if (end - i >= 2) {
      planFoldedRun(segments, i, end, depth, planned, carry);
      return end;
    }
    planned.add(new Planned(segment.nodes.get(0), depth, "- ", flush(carry)));
    return i + 1;
  }

  /**
   * Exclusive end of the maximal run of consecutive sequential segments whose subtree is
   * structurally identical to the one at {@code start}. A concurrency segment or a diverging shape
   * ends the run; a later identical sibling begins a fresh run (no folding across the divergence).
   */
  private int foldRunEnd(List<ChildSegment> segments, int start) {
    var key = STRUCTURE.subtreeKey(segments.get(start).nodes.get(0));
    var end = start + 1;
    while (end < segments.size()
        && segments.get(end).groupId == null
        && STRUCTURE.subtreeKey(segments.get(end).nodes.get(0)).equals(key)) {
      end++;
    }
    return end;
  }

  /**
   * The run's first iteration becomes a normal walk position — full recursion, exactly like any
   * other node — carrying the folded-away siblings so {@link #exitNode} can compute and append the
   * {@code ×k more …} summary once the first iteration's whole subtree is done (see {@link Ctx}'s
   * note on why that computation cannot happen here, at planning time). The folded-away iterations
   * are never walked at all: {@link LoopFold#summaryLine} names them by their own {@link
   * TreeWalk}-bounded comparison, not by rendering their subtrees.
   */
  private void planFoldedRun(
      List<ChildSegment> segments,
      int start,
      int end,
      int depth,
      List<Planned> planned,
      StringBuilder carry) {
    var first = segments.get(start).nodes.get(0);
    var folded = new ArrayList<TraceNode>();
    for (var i = start + 1; i < end; i++) {
      folded.add(segments.get(i).nodes.get(0));
    }
    var p = new Planned(first, depth, "- ", flush(carry));
    p.foldedSiblings = folded;
    planned.add(p);
  }

  private void planFireAndForget(
      TraceNode launcher, int depth, List<Planned> planned, StringBuilder carry) {
    var indent = "  ".repeat(depth);
    carry.append(indent).append("- ⤳ fire-and-forget");
    if (launcher.concurrency() != null) {
      appendGroupId(launcher, carry);
      carry.append(" [thread: ").append(launcher.concurrency().threadName());
      appendVirtual(launcher, carry);
      carry.append("]");
    }
    carry.append("\n");
    if (launcher.children().isEmpty()) {
      carry.append(indent).append("  [launched, result not captured]\n");
      return;
    }
    var children = launcher.children();
    for (var idx = 0; idx < children.size(); idx++) {
      planned.add(new Planned(children.get(idx), depth + 1, "- ", idx == 0 ? flush(carry) : null));
    }
  }

  /**
   * Every member becomes a normal walk position — full recursion via {@link #enterNode} with the
   * {@code "- ↦ "} prefix, exactly as {@link #planFoldedRun}'s first iteration does — with the
   * member's own thread-info line as its trailing text, and the group's {@code join} line (plus the
   * sequential-async hint) appended to the <em>last</em> sorted member's trailing text, since
   * {@link TreeWalk} visits the returned children in this exact order, so the last member's whole
   * subtree is the last thing rendered before the join line belongs.
   */
  private void planConcurrentGroup(
      List<TraceNode> members, int depth, List<Planned> planned, StringBuilder carry) {
    var indent = "  ".repeat(depth);
    var analysis = SequentialAsyncDetector.analyze(members);
    carry.append(indent).append("- ⑂ fork [").append(members.size()).append(" tasks]");
    appendGroupId(members.get(0), carry);
    carry.append("\n");
    var sorted = members.stream().sorted(this::compareBySigName).toList();
    var joinLine = buildJoinLine(members, indent, analysis);
    for (var i = 0; i < sorted.size(); i++) {
      var member = sorted.get(i);
      var p = new Planned(member, depth + 1, "- ↦ ", i == 0 ? flush(carry) : null);
      p.trailing =
          memberTrailing(
              member,
              depth + 1,
              analysis.isSequentialAsync(),
              i == sorted.size() - 1 ? joinLine : "");
      planned.add(p);
    }
  }

  private String memberTrailing(
      TraceNode member, int depth, boolean sequentialAsync, String joinLine) {
    var sb = new StringBuilder();
    if (member.concurrency() != null) {
      var indent = "  ".repeat(depth);
      sb.append(indent).append("      [thread: ").append(member.concurrency().threadName());
      appendVirtual(member, sb);
      sb.append(sequentialAsync ? "] [async, awaited sequentially]\n" : "]\n");
    }
    sb.append(joinLine);
    return sb.isEmpty() ? null : sb.toString();
  }

  private String buildJoinLine(
      List<TraceNode> members, String indent, SequentialAsyncResult analysis) {
    var sb = new StringBuilder();
    long wallTimeMs =
        members.stream().mapToLong(TraceNode::durationNanos).max().orElse(0) / 1_000_000;
    sb.append(indent).append("- ⑃ join — ").append(wallTimeMs).append("ms");
    appendWaitAnalysis(members, sb);
    sb.append("\n");
    if (analysis.isSequentialAsync()) {
      appendSequentialAsyncHint(analysis, indent, sb);
    }
    return sb.toString();
  }

  /**
   * Surfaces the concurrency groupId (e.g. {@code fork-7}, {@code fanf-2}) so a human reading the
   * Markdown can correlate interleaved fork/fire-and-forget subtrees — the id is captured in JSON
   * but was previously invisible here, where humans look for it.
   */
  private void appendGroupId(TraceNode node, StringBuilder sb) {
    // Reached only for segmented concurrency (fork groups, fire-and-forget), where ChildSegment
    // guarantees a non-null groupId — a null-groupId node renders on the normal sequential path.
    sb.append(" [groupId: ").append(node.concurrency().groupId()).append("]");
  }

  /**
   * Marks work that ran on a virtual thread with {@code (virtual)} inside the thread annotation —
   * the {@code virtual} flag is captured in JSON but otherwise invisible in Markdown. Rendered only
   * when true so the common platform-thread case stays terse.
   */
  private void appendVirtual(TraceNode node, StringBuilder sb) {
    if (node.concurrency().virtual()) {
      sb.append(" (virtual)");
    }
  }

  private void appendSequentialAsyncHint(
      SequentialAsyncResult analysis, String indent, StringBuilder sb) {
    sb.append(indent)
        .append("- ⚡ Sequential async: total ")
        .append(analysis.totalMillis())
        .append("ms, parallelizable to ~")
        .append(analysis.parallelizableMillis())
        .append("ms\n");
  }

  private void appendWaitAnalysis(List<TraceNode> members, StringBuilder sb) {
    if (members.size() < 2) {
      return;
    }
    var slowest =
        members.stream().max(Comparator.comparingLong(TraceNode::durationNanos)).orElseThrow();
    var fastest =
        members.stream().min(Comparator.comparingLong(TraceNode::durationNanos)).orElseThrow();
    long waitMs = (slowest.durationNanos() - fastest.durationNanos()) / 1_000_000;
    if (waitMs > 0) {
      sb.append(" (waited ")
          .append(waitMs)
          .append("ms for ")
          .append(MarkdownEscape.text(slowest.signature().className()))
          .append(" after ")
          .append(MarkdownEscape.text(fastest.signature().className()))
          .append(")");
    }
  }

  private int compareBySigName(TraceNode a, TraceNode b) {
    var keyA = a.signature().className() + "." + a.signature().methodName();
    var keyB = b.signature().className() + "." + b.signature().methodName();
    return keyA.compareTo(keyB);
  }

  private void enterNode(
      TraceNode node,
      NodeContext<TraceNode, Ctx> ctxOf,
      ValueReferenceIndex refs,
      StringBuilder sb) {
    var ctx = ctxOf.peek(node);
    if (ctx.leadingLine() != null) {
      sb.append(ctx.leadingLine());
    }
    var indent = "  ".repeat(ctx.depth());
    var sig = node.signature();
    var methodCall = formatMethodCall(sig, refs);

    sb.append(indent).append(ctx.prefix()).append(methodCall);
    if (node.children().isEmpty()) {
      renderOutcomeInline(node.outcome(), sig, ctx.depth(), sb, refs);
      renderDuration(node, sb);
      sb.append("\n");
      renderNarration(sig, indent, sb);
    } else {
      if (node.outcome() instanceof TraceOutcome.Returned) {
        renderOutcomeInline(node.outcome(), sig, ctx.depth(), sb, refs);
      }
      renderDuration(node, sb);
      sb.append("\n");
      renderNarration(sig, indent, sb);
    }
  }

  private void exitNode(
      TraceNode node,
      NodeContext<TraceNode, Ctx> ctxOf,
      ValueReferenceIndex refs,
      StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (!node.children().isEmpty()) {
      renderClosingOutcome(node, node.signature(), "  ".repeat(ctx.depth()), ctx.depth(), sb);
    }
    appendFoldSummaryIfAny(node, ctx, refs, sb);
    if (ctx.trailingLine() != null) {
      sb.append(ctx.trailingLine());
    }
  }

  /**
   * {@link LoopFold#summaryLine} mints reference labels through {@code refs} as a side effect — see
   * {@link Ctx}'s note on why this must happen here, once the node's own subtree is fully rendered,
   * rather than at planning time.
   */
  private void appendFoldSummaryIfAny(
      TraceNode node, Ctx ctx, ValueReferenceIndex refs, StringBuilder sb) {
    if (ctx.foldedSiblings() != null) {
      sb.append("  ".repeat(ctx.depth()))
          .append("- ")
          .append(LoopFold.summaryLine(node, ctx.foldedSiblings(), refs))
          .append("\n");
    }
  }

  /** A node the walk stopped at: rendered as a leaf list item, plus the marker. */
  private void limitedNode(
      TraceNode node,
      NodeContext<TraceNode, Ctx> ctxOf,
      ValueReferenceIndex refs,
      TreeWalk.Reason reason,
      StringBuilder sb) {
    var ctx = ctxOf.pop(node);
    if (ctx.leadingLine() != null) {
      sb.append(ctx.leadingLine());
    }
    var indent = "  ".repeat(ctx.depth());
    var sig = node.signature();
    var methodCall = formatMethodCall(sig, refs);
    sb.append(indent).append(ctx.prefix()).append(methodCall);
    renderOutcomeInline(node.outcome(), sig, ctx.depth(), sb, refs);
    renderDuration(node, sb);
    sb.append(" ").append(reason.marker()).append("\n");
    renderNarration(sig, indent, sb);
    // TreeWalk never calls onExit for a limited node, so the fold summary/trailing text — normally
    // appended there — is appended here instead; a limited node is treated as a leaf either way.
    appendFoldSummaryIfAny(node, ctx, refs, sb);
    if (ctx.trailingLine() != null) {
      sb.append(ctx.trailingLine());
    }
  }

  /**
   * Exceptional and in-flight outcomes close AFTER the children — chronologically where they
   * happened. Normal returns render inline on the entry line instead of a closing repeat, and void
   * completions render nothing at all.
   */
  private void renderClosingOutcome(
      TraceNode node, MethodSignature sig, String indent, int depth, StringBuilder sb) {
    if (node.outcome() instanceof TraceOutcome.Returned || node.outcome() == null) {
      return;
    }
    sb.append(indent).append("  - ");
    renderOutcomeClosing(node.outcome(), sig, depth, sb);
    sb.append("\n");
  }

  private void renderOutcomeInline(
      TraceOutcome outcome,
      MethodSignature sig,
      int depth,
      StringBuilder sb,
      ValueReferenceIndex refs) {
    if (outcome instanceof TraceOutcome.Returned r) {
      if (r.renderedValue() != null) {
        sb.append(" → ").append(MarkdownEscape.code(refs.display(r.renderedValue())));
      }
    } else if (outcome instanceof TraceOutcome.Threw t) {
      var errorIndent = "  ".repeat(depth + 1);
      sb.append("\n\n")
          .append(errorIndent)
          .append("> ❌ `")
          .append(MarkdownEscape.text(t.exception().getClass().getSimpleName()))
          .append("`: ")
          .append(MarkdownEscape.text(ExceptionMessage.text(t.exception())));
      if (sig.errorContext() != null) {
        sb.append("\n")
            .append(errorIndent)
            .append("> ")
            .append(MarkdownEscape.text(sig.errorContext()));
      }
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append(" ⏳ in-flight");
    }
  }

  private void renderOutcomeClosing(
      TraceOutcome outcome, MethodSignature sig, int depth, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Threw t) {
      var errorIndent = "  ".repeat(depth + 1);
      sb.append("❌ `")
          .append(MarkdownEscape.text(t.exception().getClass().getSimpleName()))
          .append("`: ")
          .append(MarkdownEscape.text(ExceptionMessage.text(t.exception())));
      if (sig.errorContext() != null) {
        sb.append("\n")
            .append(errorIndent)
            .append("> ")
            .append(MarkdownEscape.text(sig.errorContext()));
      }
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append("⏳ in-flight");
    }
  }

  private void renderNarration(MethodSignature sig, String indent, StringBuilder sb) {
    if (sig.narration() != null) {
      sb.append(indent).append("  *").append(MarkdownEscape.text(sig.narration())).append("*\n");
    }
  }

  private void renderDuration(TraceNode node, StringBuilder sb) {
    if (node.durationNanos() > 0) {
      sb.append(" — ").append(DurationFormat.millis(node.durationNanos())).append("ms");
      // The threshold stays a whole-millisecond comparison on the raw nanos: rendering precision
      // and "is this slow" are separate questions, and the latter must not shift with formatting.
      if (node.durationNanos() > slowThresholdMs * 1_000_000L) {
        sb.append(" ⚠️ slow");
      }
    }
  }

  private String formatMethodCall(MethodSignature sig, ValueReferenceIndex refs) {
    var params =
        sig.parameters().stream()
            .map(param -> renderParam(param, refs))
            .collect(Collectors.joining(", "));
    return "**" + signatureText(sig) + "**(" + params + ")";
  }

  private String renderParam(ParameterCapture param, ValueReferenceIndex refs) {
    var name = MarkdownEscape.text(param.name());
    if (param.redacted()) {
      return name + ": `[REDACTED]`";
    }
    if (param.renderedValue().isEmpty()) {
      // Value-suppressed at non-DETAIL levels (captured as ""); an ellipsis reads as intent, not a
      // rendering bug. Render-time only — the captured empty-string convention stays unchanged.
      return name + ": …";
    }
    return name + ": " + MarkdownEscape.code(refs.display(param.renderedValue()));
  }
}
