/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClarityResultTest {

  @Test
  void existingSevenArgConstructorDefaultsElementNotesToEmpty() {
    var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of());

    assertThat(result.elementNotes()).isEmpty();
  }

  @Test
  void fullConstructorCarriesElementNotes() {
    var note =
        new ElementNote("method", "OrderService.processOrder", 0.62, "Generic verb 'process'");
    var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of(), List.of(note));

    assertThat(result.elementNotes()).containsExactly(note);
  }
}
