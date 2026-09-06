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
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A diagram of an incomplete trace must say it is incomplete — in each format's own comment syntax,
 * so the note travels with the file without changing what a renderer draws.
 */
class DiagramLossFooterTest {

  private static final TraceLoss SHED = new TraceLoss(1204, 0, 0);

  @Test
  void mermaidCarriesTheFooterAsADiagramComment() {
    var rendered = new MermaidSequenceDiagramRenderer().render(tree(SHED));

    assertThat(rendered).contains("\n\n%% ⚠ Incomplete narrative:");
    assertThat(rendered).contains("narrativetrace.buffer.capacity");
  }

  @Test
  void mermaidWithAliasesCarriesItToo() {
    var rendered = new MermaidSequenceDiagramRenderer().renderWithAliases(tree(SHED));

    assertThat(rendered).contains("%% ⚠ Incomplete narrative:");
  }

  @Test
  void plantUmlCarriesTheFooterAfterEndumlSoTheDiagramItselfIsUnchanged() {
    var rendered = new PlantUmlSequenceDiagramRenderer().render(tree(SHED));

    assertThat(rendered).contains("@enduml\n\n' ⚠ Incomplete narrative:");
    assertThat(rendered.indexOf("@enduml")).isLessThan(rendered.indexOf("Incomplete"));
  }

  @Test
  void aCleanCaptureLeavesEveryDiagramByteForByteWhatItWas() {
    var clean = tree(TraceLoss.none());

    assertThat(new MermaidSequenceDiagramRenderer().render(clean))
        .doesNotContain("Incomplete narrative");
    assertThat(new MermaidSequenceDiagramRenderer().renderWithAliases(clean))
        .doesNotContain("Incomplete narrative");
    assertThat(new PlantUmlSequenceDiagramRenderer().render(clean))
        .doesNotContain("Incomplete narrative")
        .endsWith("@enduml");
  }

  private static TraceTree tree(TraceLoss loss) {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    return new DefaultTraceTree(List.of(node), null, loss);
  }
}
