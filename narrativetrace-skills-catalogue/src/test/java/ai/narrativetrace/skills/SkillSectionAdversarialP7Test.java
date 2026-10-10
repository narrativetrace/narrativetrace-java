/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Heading-line and body-shape edges for {@link SkillSection}. */
class SkillSectionAdversarialP7Test {

  @Test
  void aHeadingWithACarriageReturnIsRejectedAsNotOneLine() {
    // SUSPECTED BUG: only LF is refused, so a CR in a heading splits the "## " line for any
    // Markdown reader that treats CR as a line ending, and it reaches the page unchecked.
    assertThatThrownBy(() -> new SkillSection("Trace\rreading", "body"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBodyOfOnlyNewlinesIsRejectedAsBlank() {
    assertThatThrownBy(() -> new SkillSection("Shapes", "\n\n\n"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
