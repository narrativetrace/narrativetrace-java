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

import java.util.List;
import org.junit.jupiter.api.Test;

class StepBodyTest {

  @Test
  void commandStepCopiesItsCommands() {
    var step = new StepBody.CommandStep(List.of("git status"));
    assertThat(step.commands()).containsExactly("git status");
  }

  @Test
  void codeStepRejectsBlankLanguageOrCode() {
    assertThatThrownBy(() -> new StepBody.CodeStep(" ", "code"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StepBody.CodeStep("java", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void codeStepHoldsItsFields() {
    var step = new StepBody.CodeStep("java", "int x = 1;");
    assertThat(step.language()).isEqualTo("java");
    assertThat(step.code()).isEqualTo("int x = 1;");
  }

  @Test
  void snippetStepRejectsBlankLanguageOrPath() {
    assertThatThrownBy(() -> new StepBody.SnippetStep(" ", "a/b.java"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StepBody.SnippetStep("java", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void snippetStepHoldsItsFields() {
    var step =
        new StepBody.SnippetStep(
            "java", "sixty-seconds/src/main/java/com/example/orders/Main.java");
    assertThat(step.language()).isEqualTo("java");
    assertThat(step.path()).isEqualTo("sixty-seconds/src/main/java/com/example/orders/Main.java");
  }
}
