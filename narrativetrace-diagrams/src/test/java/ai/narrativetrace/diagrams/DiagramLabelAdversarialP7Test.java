/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Span-id guard for the note label: only one well-formed id may enter a diagram line. */
class DiagramLabelAdversarialP7Test {

  @ParameterizedTest
  @ValueSource(strings = {"#1.", "#1 ", " #1", "1", "#", "", "#1.2\n", "#1..2", "#1a", "#1.2 #1.3"})
  void spanIdRefusesAnythingThatIsNotExactlyOneWellFormedId(String id) {
    assertThatThrownBy(() -> DiagramLabel.spanId(id))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("not a span id");
  }

  @Test
  void spanIdRefusesNull() {
    assertThatThrownBy(() -> DiagramLabel.spanId(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void spanIdKeepsAWellFormedDeepIdVerbatim() {
    assertThat(DiagramLabel.spanId("#1.20.3").text()).isEqualTo("#1.20.3");
  }
}
