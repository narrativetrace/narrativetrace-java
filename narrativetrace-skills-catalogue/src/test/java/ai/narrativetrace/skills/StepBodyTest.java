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
