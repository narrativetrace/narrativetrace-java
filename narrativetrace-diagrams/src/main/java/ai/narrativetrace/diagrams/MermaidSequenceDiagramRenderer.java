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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Mermaid sequence-diagram renderer.
 *
 * <p>INTENT: Use this for Markdown-friendly sequence diagrams that can be rendered by Mermaid-aware
 * tooling.
 *
 * <p><b>@llmNote</b> This class implements {@link NarrativeRenderer} — a BSL module implementing an
 * Apache interface, the same shape core's own renderers use. {@link #renderWithAliases} is the
 * extra entry point the interface has no room for; get it from {@link
 * SequenceDiagramRenderers#mermaid} when only the interface's {@code render} is needed.
 *
 * <p><b>@edgeCase</b> Every walk here goes through {@link SequenceWalk} (which shares {@link
 * ai.narrativetrace.core.tree.TreeWalk} with {@link PlantUmlSequenceDiagramRenderer}): a
 * hand-built, replayed or deserialized tree is not guaranteed acyclic ({@code TraceNode.children}
 * is an undefended list), and a genuinely deep tree is ordinary for a recursive business method. A
 * node beyond the walk's depth limit or already on the current path still gets its own call arrow
 * and outcome — rendered exactly like a leaf — plus a {@code Note over} statement carrying the
 * reason; the walk simply never descends into its children.
 */
public final class MermaidSequenceDiagramRenderer implements NarrativeRenderer {

  /**
   * Renders the tree as a Mermaid {@code sequenceDiagram}, one participant per class.
   *
   * @param tree the captured trace to draw
   * @return Mermaid source, ready to paste into a fenced {@code mermaid} block. Never {@code null}.
   */
  @Override
  public String render(TraceTree tree) {
    var grammar = MermaidSequenceGrammar.INSTANCE;
    var sb = new StringBuilder();
    sb.append(grammar.header());

    for (var participant : SequenceParticipants.collect(tree.roots())) {
      sb.append(grammar.participant(DiagramLabel.quotedIdentifier(participant)));
    }

    for (var root : tree.roots()) {
      SequenceWalk.render(root, grammar, DiagramLabel::quotedIdentifier, sb);
    }

    sb.append(grammar.footer());
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
    var grammar = MermaidSequenceGrammar.INSTANCE;
    var sb = new StringBuilder();
    sb.append(grammar.header());

    var participants = SequenceParticipants.collect(tree.roots());
    var aliases = buildAliases(participants);

    for (var participant : participants) {
      var aliasLabel = aliases.get(participant);
      var displayLabel = DiagramLabel.quotedIdentifier(participant);
      sb.append(grammar.participant(aliasLabel.aliasedAs(displayLabel)));
    }

    for (var root : tree.roots()) {
      SequenceWalk.render(root, grammar, aliases::get, sb);
    }

    sb.append(grammar.footer());
    return sb.toString().stripTrailing() + LossFooter.block(tree, "%% ");
  }

  private Map<String, DiagramLabel> buildAliases(LinkedHashSet<String> participants) {
    var aliases = new LinkedHashMap<String, DiagramLabel>();
    var usedAliases = new LinkedHashSet<String>();
    for (var name : participants) {
      var alias = extractUpperCase(name);
      if (alias.isEmpty()) {
        alias = DiagramLabel.alias(name).text();
      }
      if (usedAliases.contains(alias)) {
        int suffix = 2;
        while (usedAliases.contains(alias + suffix)) {
          suffix++;
        }
        alias = alias + suffix;
      }
      usedAliases.add(alias);
      aliases.put(name, DiagramLabel.identifier(alias));
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
}
