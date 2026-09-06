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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PlantUML sequence-diagram renderer.
 *
 * <p>INTENT: Use this when your documentation or CI stack renders PlantUML rather than Mermaid.
 *
 * <p><b>@llmNote</b> This class does not implement the core {@code NarrativeRenderer} interface.
 * Adapt it with a method reference such as {@code new PlantUmlSequenceDiagramRenderer()::render}.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link TreeWalk}: a hand-built, replayed or
 * deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an undefended list),
 * and a genuinely deep tree is ordinary for a recursive business method. A node beyond {@link
 * TreeWalk#MAX_DEPTH} or already on the current path still gets its own call arrow and outcome —
 * rendered exactly like a leaf — plus an {@code hnote over} statement carrying {@link
 * TreeWalk.Reason#marker()}; the walk simply never descends into its children.
 */
public final class PlantUmlSequenceDiagramRenderer {

  /**
   * Renders the tree as a PlantUML sequence diagram, one participant per class.
   *
   * @param tree the captured trace to draw
   * @return PlantUML source between {@code @startuml} and {@code @enduml}. Never {@code null}.
   */
  public String render(TraceTree tree) {
    var sb = new StringBuilder();
    sb.append("@startuml\n");

    for (var participant : collectParticipants(tree.roots())) {
      sb.append("participant ").append(quoteIfNeeded(participant)).append("\n");
    }

    for (var root : tree.roots()) {
      renderTree(root, sb);
    }

    sb.append("@enduml");
    // The comment sits after @enduml on purpose: PlantUML stops parsing there, so a lossy diagram
    // renders identically to a clean one and the note is still in the file a human opens.
    return sb.toString().stripTrailing() + LossFooter.block(tree, "' ");
  }

  /**
   * Walks one root, bounded and cycle-safe, emitting a call arrow on the way in and the outcome
   * arrow/note on the way out — {@link TreeWalk}'s enter/exit pair matches this method's own
   * enter-then-recurse-then-outcome shape exactly.
   */
  private void renderTree(TraceNode root, StringBuilder sb) {
    Deque<String> callerChain = new ArrayDeque<>();
    TreeWalk.walk(
        root,
        TraceNode::children,
        (node, depth) -> enterNode(node, callerChain, sb),
        (node, depth) -> exitNode(node, callerChain, sb),
        (node, depth, reason) -> limitedNode(node, callerChain, sb, reason));
  }

  private void enterNode(TraceNode node, Deque<String> callerChain, StringBuilder sb) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, sb);
    callerChain.push(target);
  }

  private void exitNode(TraceNode node, Deque<String> callerChain, StringBuilder sb) {
    var target = callerChain.pop();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendOutcome(node, caller, target, sb);
  }

  /** A node the walk stopped at: rendered as a leaf (arrow in, outcome out), plus the marker. */
  private void limitedNode(
      TraceNode node, Deque<String> callerChain, StringBuilder sb, TreeWalk.Reason reason) {
    var target = node.signature().className();
    var caller = callerChain.isEmpty() ? target : callerChain.peek();
    appendCallArrow(node, caller, target, sb);
    appendOutcome(node, caller, target, sb);
    sb.append("hnote over ")
        .append(quoteIfNeeded(target))
        .append(" : ")
        .append(reason.marker())
        .append("\n");
  }

  private void appendCallArrow(TraceNode node, String caller, String target, StringBuilder sb) {
    var method = DiagramText.identifier(node.signature().methodName());
    var params =
        node.signature().parameters().stream()
            .map(p -> DiagramText.identifier(p.name()))
            .collect(Collectors.joining(", "));

    sb.append(quoteIfNeeded(caller))
        .append(" -> ")
        .append(quoteIfNeeded(target))
        .append(": ")
        .append(method)
        .append("(")
        .append(params)
        .append(")\n");
  }

  private void appendOutcome(TraceNode node, String caller, String target, StringBuilder sb) {
    var outcome = node.outcome();
    if (outcome instanceof TraceOutcome.Returned returned) {
      sb.append(quoteIfNeeded(target))
          .append(" --> ")
          .append(quoteIfNeeded(caller))
          .append(": ")
          .append(DiagramText.returnMessage(returned.renderedValue(), returned.structuredValue()))
          .append("\n");
    } else if (outcome instanceof TraceOutcome.Threw threw) {
      sb.append(quoteIfNeeded(target))
          .append(" -[#red]-> ")
          .append(quoteIfNeeded(caller))
          .append(": ")
          .append(DiagramText.identifier(threw.exception().getClass().getSimpleName()))
          .append("\n");
    } else if (outcome instanceof TraceOutcome.Incomplete) {
      sb.append("hnote over ").append(quoteIfNeeded(target)).append(" : in-flight\n");
    }
  }

  /**
   * Quotes a participant name, after {@link DiagramText#identifier} has made it safe to quote.
   *
   * <p><b>@edgeCase</b> Sanitising happens here rather than at each call site so no future caller
   * can reach the quoting without it. A newline in a name was previously enough to start a new
   * PlantUML statement — including a forged {@code note over}, or a {@code !include} preprocessor
   * directive.
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
