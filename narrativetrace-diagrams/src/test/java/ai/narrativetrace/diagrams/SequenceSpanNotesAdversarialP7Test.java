/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each call arrow's span note cites the same position path in both sequence grammars. */
class SequenceSpanNotesAdversarialP7Test {

  private final MermaidSequenceDiagramRenderer mermaid = new MermaidSequenceDiagramRenderer();
  private final PlantUmlSequenceDiagramRenderer plantUml = new PlantUmlSequenceDiagramRenderer();

  @Test
  void aliasedMermaidNotesCiteEachSpanIdInCallOrder() {
    var tree = twoLevelTree();

    var notes = idsAfter(mermaid.renderWithAliases(tree), "    Note over ");

    assertThat(notes).containsExactly("#1", "#1.1");
  }

  @Test
  void plainMermaidNotesCiteEachSpanIdInCallOrder() {
    assertThat(idsAfter(mermaid.render(twoLevelTree()), "    Note over "))
        .containsExactly("#1", "#1.1");
  }

  @Test
  void plantUmlNotesCiteTheSameSpanIdsAsMermaid() {
    assertThat(idsAfter(plantUml.render(twoLevelTree()), "hnote over "))
        .containsExactly("#1", "#1.1");
  }

  private static List<String> idsAfter(String diagram, String prefix) {
    return diagram
        .lines()
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(line.lastIndexOf(": ") + 2))
        .toList();
  }

  private static DefaultTraceTree twoLevelTree() {
    var child =
        new TraceNode(
            new MethodSignature("Ledger", "write", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            0L);
    var root =
        new TraceNode(
            new MethodSignature("TripService", "record", List.of()),
            List.of(child),
            new TraceOutcome.Returned(null),
            0L);
    return new DefaultTraceTree(List.of(root));
  }
}
