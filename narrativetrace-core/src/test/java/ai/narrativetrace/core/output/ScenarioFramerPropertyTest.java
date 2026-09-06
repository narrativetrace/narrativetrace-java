/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class ScenarioFramerPropertyTest {

  @Property
  void humanizeNeverCrashes(@ForAll String input) {
    var result = ScenarioFramer.humanize(input);
    assertThat(result).isNotNull();
  }

  @Property
  void humanizePreservesFirstCharUppercased(
      @ForAll @AlphaChars @StringLength(min = 2, max = 50) String input) {
    var result = ScenarioFramer.humanize(input);
    assertThat(result).isNotEmpty();
    assertThat(Character.isUpperCase(result.charAt(0)))
        .as("first character of '%s' (from '%s') should be uppercase", result, input)
        .isTrue();
    // No uppercase letters should remain mid-word after splitting
    var afterFirst = result.substring(1);
    for (int i = 0; i < afterFirst.length(); i++) {
      char c = afterFirst.charAt(i);
      if (Character.isUpperCase(c)) {
        // Uppercase is only valid immediately after a space (word boundary)
        assertThat(i)
            .as("uppercase '%c' at position %d in '%s' must follow a space", c, i + 1, result)
            .isGreaterThan(0);
        assertThat(afterFirst.charAt(i - 1))
            .as("character before uppercase '%c' at position %d in '%s'", c, i + 1, result)
            .isEqualTo(' ');
      }
    }
  }

  @Property
  void frameAlwaysPrefixesWithScenario(
      @ForAll @AlphaChars @StringLength(min = 1, max = 30) String input) {
    var result = ScenarioFramer.frame(input);
    assertThat(result).startsWith("Scenario: ");
    // The suffix must equal the humanized form
    assertThat(result.substring("Scenario: ".length())).isEqualTo(ScenarioFramer.humanize(input));
  }
}
