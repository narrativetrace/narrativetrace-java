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

class SkillSectionTest {

  @Test
  void holdsItsHeadingAndMarkdown() {
    var section = new SkillSection("How to read a trace", "| a | b |\n");
    assertThat(section.heading()).isEqualTo("How to read a trace");
    assertThat(section.markdown()).isEqualTo("| a | b |\n");
  }

  @Test
  void rejectsABlankOrNullHeadingOrBody() {
    assertThatThrownBy(() -> new SkillSection(" ", "body"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SkillSection(null, "body"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SkillSection("heading", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SkillSection("heading", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAHeadingThatWouldBreakTheMarkdownHeadingLine() {
    assertThatThrownBy(() -> new SkillSection("two\nlines", "body"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SkillSection("# nested", "body"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
