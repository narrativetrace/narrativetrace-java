/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.LossFooter;

/**
 * PlantUML sequence-diagram renderer.
 *
 * <p>INTENT: Use this when your documentation or CI stack renders PlantUML rather than Mermaid.
 *
 * <p><b>@llmNote</b> This class implements {@link NarrativeRenderer} — a BSL module implementing an
 * Apache interface, the same shape core's own renderers use. Get an instance from {@link
 * SequenceDiagramRenderers#plantUml} when a caller wants the format without naming this class.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link SequenceWalk} (which shares {@link
 * ai.narrativetrace.core.tree.TreeWalk} with {@link MermaidSequenceDiagramRenderer}): a hand-built,
 * replayed or deserialized tree is not guaranteed acyclic ({@code TraceNode.children} is an
 * undefended list), and a genuinely deep tree is ordinary for a recursive business method. A node
 * beyond the walk's depth limit or already on the current path still gets its own call arrow and
 * outcome — rendered exactly like a leaf — plus an {@code hnote over} statement carrying the
 * reason; the walk simply never descends into its children.
 */
public final class PlantUmlSequenceDiagramRenderer implements NarrativeRenderer {

  /**
   * Renders the tree as a PlantUML sequence diagram, one participant per class.
   *
   * @param tree the captured trace to draw
   * @return PlantUML source between {@code @startuml} and {@code @enduml}. Never {@code null}.
   */
  @Override
  public String render(TraceTree tree) {
    var grammar = PlantUmlSequenceGrammar.INSTANCE;
    var sb = new StringBuilder();
    sb.append(grammar.header());

    for (var participant : SequenceParticipants.collect(tree.roots())) {
      sb.append(grammar.participant(DiagramLabel.plainToken(participant)));
    }

    for (var root : tree.roots()) {
      SequenceWalk.render(root, grammar, DiagramLabel::plainToken, sb);
    }

    sb.append(grammar.footer());
    // The comment sits after @enduml on purpose: PlantUML stops parsing there, so a lossy diagram
    // renders identically to a clean one and the note is still in the file a human opens.
    return sb.toString().stripTrailing() + LossFooter.block(tree, "' ");
  }
}
