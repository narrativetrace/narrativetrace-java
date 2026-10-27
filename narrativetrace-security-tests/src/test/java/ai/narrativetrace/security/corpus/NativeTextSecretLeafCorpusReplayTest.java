/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.render.ValueRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Corpus replay for the {@code native-text-secret-leaf} row: a secret in a leaf's own text, with no
 * field name or annotation for the name deny-list to match.
 *
 * <p>INTENT: {@link HostileCorpusTest#everyDeclaredGraphShapeBuilds} proves the row builds; this
 * proves the property the row exists for, one element at a time so a failure names the leaf. Every
 * one of the five must come out as the marker on the flat path and on the structured path, the pair
 * the routing parity replay holds together everywhere else.
 */
class NativeTextSecretLeafCorpusReplayTest {

  private static final String ROW_ID = "native-text-secret-leaf";

  private final ValueRenderer renderer = new ValueRenderer();

  @SuppressWarnings("unchecked")
  private static List<Object> leaves() {
    var row =
        HostileCorpus.graphs().stream()
            .filter(c -> c.id().equals(ROW_ID))
            .findFirst()
            .orElseThrow(() -> new AssertionError(ROW_ID + " must be in graphs.json"));
    return (List<Object>) HostileGraphs.build(row, "sentinel-native-text");
  }

  @Test
  void theRowHoldsFiveLeaves() {
    assertThat(leaves()).hasSize(5);
  }

  @Test
  void everyLeafRendersTheMarkerOnTheFlatPath() {
    for (var leaf : leaves()) {
      assertThat(renderer.render(leaf))
          .as("flat path, %s", leaf.getClass().getSimpleName())
          .isEqualTo(RedactionPolicy.MARKER);
    }
  }

  @Test
  void everyLeafRendersTheMarkerOnTheStructuredPath() {
    for (var leaf : leaves()) {
      assertThat(renderer.renderStructured(leaf))
          .as("structured path, %s", leaf.getClass().getSimpleName())
          .isEqualTo(new RenderedValue.StringVal(RedactionPolicy.MARKER));
    }
  }

  @Test
  void theSecretTextReachesNeitherPathWhenTheFiveSitInOneList() {
    var leaves = leaves();

    assertThat(renderer.render(leaves))
        .doesNotContain(HostileGraphs.JWT_SHAPE)
        .doesNotContain(HostileGraphs.CARD_SHAPE);
    assertThat(String.valueOf(renderer.renderStructured(leaves)))
        .doesNotContain(HostileGraphs.JWT_SHAPE)
        .doesNotContain(HostileGraphs.CARD_SHAPE);
  }
}
