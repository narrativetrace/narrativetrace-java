/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the one-line summary that stands in for the folded-away iterations of a loop.
 *
 * <p>INTENT: When a maximal run of {@code k+1} consecutive same-shape sibling subtrees is folded,
 * {@code MarkdownRenderer} renders the first iteration in full and calls this to describe the
 * remaining {@code k}: {@code ×k more: ‹Dinner›, ‹Taxi› — same flow (validate ✓ → record ✓) — 12ms
 * total, 2–5ms each}. The run has already been proven structurally identical (same signatures,
 * child shape, outcome kinds); what varies is captured values, so the line names each folded
 * iteration by its first distinguishing root argument through the item-13 identity ladder and keeps
 * that label consistent with later {@code ‹ref›} uses via {@link ValueReferenceIndex}.
 *
 * <p><b>@llmNote</b> Presentation only, Markdown only. This never runs for concurrency members —
 * fork groups and fire-and-forget subtrees are not sequential iterations and never fold.
 *
 * <p><b>@llmNote</b> Item 18 (intra-trace value deltas) met folding here: when a folded iteration
 * is a changed re-capture of a value the document already defines in full, it is named by its diff
 * ({@code ‹Dinner›′{currency: "USD"→"EUR"}}) rather than by a bare label the reader cannot resolve.
 * Everything else still resolves to a label, or to a positional {@code #n}.
 */
final class LoopFold {

  /** Beyond this many distinguishing labels the list is capped with an ellipsis to stay legible. */
  private static final int MAX_LABELS = 6;

  /** Beyond this many direct children the flow tail is capped with an ellipsis. */
  private static final int MAX_FLOW = 8;

  /**
   * Characters of a distinguishing scalar the fold line shows before eliding the rest.
   *
   * <p>Long enough for a SKU, an id or a short amount — the things that actually separate two
   * iterations — and short enough that several of them still fit on one line.
   */
  private static final int MAX_VALUE_LENGTH = 32;

  private LoopFold() {}

  /**
   * Renders the {@code ×k more …} body (no leading indent or bullet) for the {@code folded}
   * iterations that follow the fully-rendered {@code first}.
   *
   * @param first the first iteration of the run, rendered in full elsewhere
   * @param folded the remaining, folded-away iterations (never empty)
   * @param refs the pass-wide reference index, so labels minted here match later {@code ‹ref›} uses
   */
  static String summaryLine(TraceNode first, List<TraceNode> folded, ValueReferenceIndex refs) {
    var sb = new StringBuilder();
    sb.append("×").append(folded.size()).append(" more");
    var labels = labels(first, folded, refs);
    if (labels.isEmpty()) {
      sb.append(" (identical)");
    } else {
      sb.append(": ").append(String.join(", ", labels));
    }
    appendFlow(first, sb);
    appendDurations(folded, sb);
    return sb.toString();
  }

  private static List<String> labels(
      TraceNode first, List<TraceNode> folded, ValueReferenceIndex refs) {
    var labels = new ArrayList<String>();
    for (var i = 0; i < folded.size(); i++) {
      var label = label(first, folded.get(i), i + 2, refs);
      if (label != null) {
        labels.add(label);
      }
      if (labels.size() > MAX_LABELS) {
        labels.set(MAX_LABELS, "…");
        return labels.subList(0, MAX_LABELS + 1);
      }
    }
    return labels;
  }

  /**
   * Names one folded iteration by the first root argument whose rendered bytes differ from the
   * first iteration's — as a diff when the document already defines that entity, otherwise through
   * the identity ladder; {@code null} when the iteration is value-identical to the first (nothing
   * to distinguish), a positional {@code #n} when only a deeper value differs.
   */
  private static String label(
      TraceNode first, TraceNode node, int position, ValueReferenceIndex refs) {
    var firstParams = first.signature().parameters();
    var params = node.signature().parameters();
    for (var i = 0; i < params.size(); i++) {
      var p = params.get(i);
      if (!p.redacted() && !p.renderedValue().equals(firstParams.get(i).renderedValue())) {
        var label = refs.foldDisplay(p.renderedValue(), p.structuredValue());
        return label != null ? label : namedValue(position, p);
      }
    }
    return valueKey(first).equals(valueKey(node)) ? null : "#" + position;
  }

  /**
   * A folded iteration named by the argument that distinguishes it, beside the position that
   * locates it: {@code #2 sku=`"TENT"`}.
   *
   * <p>INTENT: The identity ladder mints a {@code ‹label›} only from a structured value with an
   * identity field, and a loop's distinguishing argument is very often a plain scalar — a SKU, an
   * id, an amount. Those fell through to a bare {@code #2}, which named the iteration without
   * saying anything about it: two catalog lookups with different SKUs and prices rendered as the
   * first lookup plus {@code ×1 more: #2}, and the second SKU was unrecoverable from the artifact
   * (2026-09-08 agent evaluation). The position stays because it is what lets a reader find the
   * same iteration in the JSON artifact or in an unfolded render.
   *
   * <p><b>@edgeCase</b> The value is caller-influenced text going into a Markdown line, so it is
   * escaped through the same code-span sink every other rendered value uses, and elided at {@link
   * #MAX_VALUE_LENGTH} so one long argument cannot swallow the line.
   */
  private static String namedValue(int position, ParameterCapture parameter) {
    return "#"
        + position
        + " "
        + MarkdownEscape.text(parameter.name())
        + "="
        + MarkdownEscape.code(elided(parameter.renderedValue()));
  }

  /** The value, cut on a code-point boundary so a surrogate pair is never split in half. */
  private static String elided(String rendered) {
    if (rendered.length() <= MAX_VALUE_LENGTH) {
      return rendered;
    }
    var end = MAX_VALUE_LENGTH;
    if (Character.isHighSurrogate(rendered.charAt(end - 1))) {
      end--;
    }
    return rendered.substring(0, end) + "…";
  }

  private static void appendFlow(TraceNode first, StringBuilder sb) {
    if (first.children().isEmpty()) {
      return;
    }
    var parts = new ArrayList<String>();
    for (var child : first.children()) {
      if (parts.size() == MAX_FLOW) {
        parts.add("…");
        break;
      }
      parts.add(child.signature().methodName() + outcomeMark(child.outcome()));
    }
    sb.append(" — same flow (").append(String.join(" → ", parts)).append(")");
  }

  private static String outcomeMark(TraceOutcome outcome) {
    if (outcome instanceof TraceOutcome.Threw) {
      return " !";
    }
    if (outcome instanceof TraceOutcome.Incomplete) {
      return " ?";
    }
    return " ✓";
  }

  private static void appendDurations(List<TraceNode> folded, StringBuilder sb) {
    long totalNanos = folded.stream().mapToLong(TraceNode::durationNanos).sum();
    if (totalNanos == 0) {
      return;
    }
    long total = 0;
    long min = Long.MAX_VALUE;
    long max = 0;
    for (var node : folded) {
      long ms = node.durationMillis();
      total += ms;
      min = Math.min(min, ms);
      max = Math.max(max, ms);
    }
    sb.append(" — ").append(total).append("ms total, ");
    sb.append(min == max ? min + "ms each" : min + "–" + max + "ms each");
  }

  /**
   * Value-bearing key for one subtree: signatures, rendered parameter and outcome values, and
   * children recursively. Durations, timestamps, and thread identity are excluded, so two
   * iterations that carry the same data are "identical" even if they took different wall-clock
   * times. Distinct from {@code StructuralTraceRenderer.subtreeKey} (the value-free fold oracle) —
   * this finer key only decides {@code (identical)} versus a positional label.
   *
   * <p>Redacted parameters are keyed by a constant marker rather than their captured value, so a
   * removed value can never re-enter the key. This is defensive only: {@code @NotTraced} is a
   * static property of the method, so redaction is identical across every iteration of a loop and a
   * redacted parameter never distinguishes two folded siblings either way.
   */
  /**
   * Walked through {@link TreeWalk}, bounded and cycle-safe: a folded run is proven structurally
   * identical by {@code StructuralTraceRenderer.subtreeKey} first, but that proof says nothing
   * about depth or cycles, so this comparison needs its own bound regardless of how deep or
   * self-referential the (already fold-eligible) subtree is. A node beyond the bound closes
   * immediately, matching a leaf — the same "still counts as itself" convention every walker here
   * shares.
   */
  private static String valueKey(TraceNode node) {
    var sb = new StringBuilder();
    TreeWalk.walk(
        node,
        TraceNode::children,
        (n, depth) -> appendValueKeyHeader(n, sb),
        (n, depth) -> sb.append('}'),
        (n, depth, reason) -> {
          appendValueKeyHeader(n, sb);
          sb.append('}');
        });
    return sb.toString();
  }

  private static void appendValueKeyHeader(TraceNode node, StringBuilder sb) {
    var sig = node.signature();
    sb.append(sig.className()).append('.').append(sig.methodName()).append('(');
    for (var p : sig.parameters()) {
      sb.append(p.name()).append('=').append(p.redacted() ? "[R]" : p.renderedValue()).append(';');
    }
    sb.append(')');
    outcomeValueKey(node.outcome(), sb);
    sb.append('{');
  }

  /**
   * Appends only the value-bearing part of an outcome. The structural oracle already guarantees a
   * folded run shares one outcome kind at every node, so the kind itself never distinguishes
   * iterations — only a differing return value or exception message can, and {@link
   * TraceOutcome.Incomplete} carries neither.
   */
  private static void outcomeValueKey(TraceOutcome outcome, StringBuilder sb) {
    if (outcome instanceof TraceOutcome.Returned r) {
      sb.append("R:").append(r.renderedValue());
    } else if (outcome instanceof TraceOutcome.Threw t) {
      sb.append("T:")
          .append(t.exception().getClass().getName())
          .append(':')
          .append(ExceptionMessage.of(t.exception()));
    }
  }
}
