/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
