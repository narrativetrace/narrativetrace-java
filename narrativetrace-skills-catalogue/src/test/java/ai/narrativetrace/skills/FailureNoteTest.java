/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FailureNoteTest {

  @Test
  void holdsItsThreeFields() {
    var note = new FailureNote("symptom", "cause", "fix");
    assertThat(note.symptom()).isEqualTo("symptom");
    assertThat(note.cause()).isEqualTo("cause");
    assertThat(note.fix()).isEqualTo("fix");
  }

  @Test
  void rejectsBlankFields() {
    assertThatThrownBy(() -> new FailureNote(" ", "cause", "fix"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FailureNote("symptom", " ", "fix"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FailureNote("symptom", "cause", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
