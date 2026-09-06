/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.LossFooter;
import ai.narrativetrace.core.tree.TreeWalk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mermaid sequence-diagram renderer.
 *
 * <p>INTENT: Use this for Markdown-friendly sequence diagrams that can be rendered by Mermaid-aware
 * tooling.
 *
 * <p><b>@llmNote</b> This class does not implement the core {@code NarrativeRenderer} interface.
 * Adapt it with a method reference such as {@code new MermaidSequenceDiagramRenderer()::render}.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own call arrow and outcome —
 * rendered exactly like a leaf — plus a {@code Note over} statement carrying {@link
 * TreeWalk.Reason#marker()}; the walk simply never descends into its children.
 */
public final class MermaidSequenceDiagramRenderer {

  /**
   * Renders the tree as a Mermaid {@code sequenceDiagram}, one participant per class.
   *
   * @param tree the captured trace to draw
   * @return Mermaid source, ready to paste into a fenced {@code mermaid} block. Never {@code null}.
   */
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    sb.append("sequenceDiagram\n");

    for (var participant : collectParticipants(tree.roots())) {
      sb.append("    participant ").append(quoteIfNeeded(participant)).append("\n");
    }

    for (var root : tree.roots()) {
      renderTree(root, MermaidSequenceDiagramRenderer::quoteIfNeeded, sb);
    }

    return sb.toString().stripTrailing() + LossFooter.block(tree, "%% ");
  }

  /**
   * The same diagram with short participant aliases, for trees whose class names are long enough to
   * make the arrows unreadable.
   *
   * @param tree the captured trace to draw
   * @return Mermaid source declaring {@code participant X as ClassName}. Never {@code null}.
   */
  public String renderWithAliases(TraceTree tree) {
    var sb = new StringBuilder();
    sb.append("sequenceDiagram\n");

    var participants = collectParticipants(tree.roots());
    var aliases = buildAliases(participants);

    for (var participant : participants) {
      sb.append("    participant ")
          .append(aliases.get(participant))
          .append(" as ")
          .append(quoteIfNeeded(participant))
          .append("\n");
    }

    for (var root : tree.roots()) {
      renderTree(root, aliases::get, sb);
    }

    return sb.toString().stripTrailing() + LossFooter.block(tree, "%% ");
  }

  /**
   * Walks one root, bounded and cycle-safe, emitting a call arrow on the way in and the outcome
   * arrow/note on the way out — {@link TreeWalk}'s enter/exit pair matches this method's own
   * enter-then-recurse-then-outcome shape exactly.
   *
   * @param label how a class name becomes the text an arrow names — {@link #quoteIfNeeded} for
   *     plain participant names, an alias lookup for {@link #renderWithAliases}
   */
  private void renderTree(TraceNode root, Function<String, String> label, StringBuilder sb) {
    Deque<String> callerChain = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> enterNode(node, callerChain, label, sb),
        (node, depth) -> exitNode(node, callerChain, label, sb),
        (node, depth, reason) -> limitedNode(node, callerChain, label, sb, reason));
  }

  private void enterNode(
      TraceNode node, Deque<String> callerChain, Function<String, String> label, StringBuilder sb) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, label, sb);
    callerChain.push(target);
  }

  private void exitNode(
      TraceNode node, Deque<String> callerChain, Function<String, String> label, StringBuilder sb) {
    var target = callerChain.pop();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendOutcome(node, caller, target, label, sb);
  }

  /** A node the walk stopped at: rendered as a leaf (arrow in, outcome out), plus the marker. */
  private void limitedNode(
      TraceNode node,
      Deque<String> callerChain,
      Function<String, String> label,
      StringBuilder sb,
      TreeWalk.Reason reason) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, label, sb);
    appendOutcome(node, caller, target, label, sb);
    sb.append("    Note over ")
        .append(label.apply(target))
        .append(": ")
        .append(reason.marker())
        .append("\n");
  }

  private void appendCallArrow(
      TraceNode node,
      String caller,
      String target,
      Function<String, String> label,
      StringBuilder sb) {
    var method = DiagramText.identifier(node.signature().methodName());
    var params =
        node.signature().parameters().stream()
            .map(p -> DiagramText.identifier(p.name()))
            .collect(Collectors.joining(", "));

    sb.append("    ")
        .append(label.apply(caller))
        .append("->>")
        .append(label.apply(target))
        .append(": ")
        .append(method)
        .append("(")
        .append(params)
        .append(")\n");
  }

  private void appendOutcome(
      TraceNode node,
      String caller,
      String target,
      Function<String, String> label,
      StringBuilder sb) {
    var outcome = node.outcome();
    if (outcome instanceof TraceOutcome.Returned returned) {
      sb.append("    ")
          .append(label.apply(target))
          .append("-->>")
          .append(label.apply(caller))
          .append(": ")
          .append(DiagramText.returnMessage(returned.renderedValue(), returned.structuredValue()))
          .append("\n");
    } else if (outcome instanceof TraceOutcome.Threw threw) {
      sb.append("    ")
          .append(label.apply(target))
          .append("-x")
          .append(label.apply(caller))
          .append(": ")
          .append(DiagramText.identifier(threw.exception().getClass().getSimpleName()))
          .append("\n");
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append("    Note over ").append(label.apply(target)).append(": in-flight\n");
    }
  }

  private Map<String, String> buildAliases(LinkedHashSet<String> participants) {
    var aliases = new LinkedHashMap<String, String>();
    var usedAliases = new LinkedHashSet<String>();
    for (var name : participants) {
      var alias = extractUpperCase(name);
      if (alias.isEmpty()) {
        alias = DiagramText.aliasToken(name);
      }
      if (usedAliases.contains(alias)) {
        int suffix = 2;
        while (usedAliases.contains(alias + suffix)) {
          suffix++;
        }
        alias = alias + suffix;
      }
      usedAliases.add(alias);
      aliases.put(name, alias);
    }
    return aliases;
  }

  private String extractUpperCase(String name) {
    var sb = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (Character.isUpperCase(c)) {
        sb.append(c);
      }
    }
    return sb.length() >= 2 ? sb.substring(0, 2) : sb.toString();
  }

  /**
   * Quotes a participant name, after {@link DiagramText#identifier} has made it safe to quote.
   *
   * <p><b>@edgeCase</b> Sanitising happens here rather than at each call site so no future caller
   * can reach the quoting without it. The old version wrapped a name in quotes when it contained
   * {@code . - :} or a space and escaped nothing, which made an embedded {@code "} plus newline a
   * breakout into arbitrary Mermaid statements.
   */
  private static String quoteIfNeeded(String name) {
    var safe = DiagramText.identifier(name);
    if (safe.chars()
        .anyMatch(c -> c == '.' || c == '-' || c == ':' || c == ' ' || c == '<' || c == '>')) {
      return "\"" + safe + "\"";
    }
    return safe;
  }

  private LinkedHashSet<String> collectParticipants(List<TraceNode> nodes) {
    var result = new LinkedHashSet<String>();
    for (var root : nodes) {
      TreeWalk.walk(
          root,
          TraceNode::children,
          (n, depth) -> result.add(n.signature().className()),
          (n, depth, reason) -> result.add(n.signature().className()));
    }
    return result;
  }
}
