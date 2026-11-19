/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.FrontmatterBuilder;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import ai.narrativetrace.security.oracle.Formats;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-11-02-3 — {@code FrontmatterBuilder.isPlainScalar} decides whether a scenario may stand
 * unquoted from the characters it contains, which closes every way a value could become a parse
 * error or a sibling key and none of the ways a bare scalar is <em>resolved</em> to another type. A
 * scenario is a string; the frontmatter the tooling reads back reports a {@code Date}, a {@code
 * Boolean} or nothing at all.
 *
 * <p>INTENT: the oracle is the suite's third — well-formedness read back by the consumer's own
 * parser, through {@code Formats.frontmatterOf} — carried one step past "it parses". Frontmatter is
 * "the machine-readable half of a trace document" and the field's declared type is the contract: a
 * consumer that casts {@code scenario} to {@code String} throws {@code ClassCastException} on a
 * document NarrativeTrace wrote itself, and a consumer that only reads it shows the wrong scenario.
 * The {@code scenario} is the JUnit display name ({@code NarrativeTraceExtension:620}), so the
 * reachable inputs are ordinary: a dated regression test, or one named for a flag's state.
 *
 * <p><b>@llmNote</b> Suspected cause {@code FrontmatterBuilder#isPlainScalar} — the allow-list
 * admits letters, digits, space, {@code .}, {@code _} and {@code -}, which is exactly the character
 * set YAML's own int, float, bool and timestamp resolvers match on. Quoting a value whose bare form
 * resolves to a non-string tag is the missing clause; the existing {@code
 * FrontmatterMetadataInjectionTest} asserts on the emitted text only, so no test reads the document
 * back as a typed mapping.
 */
class FrontmatterScalarResolutionTest {

  @Test
  @DisplayName("ADV-2026-11-02-3: a date-shaped scenario is read back as the text it was")
  void aDateShapedScenarioIsReadBackAsText() {
    assertThat(scenarioReadBack("2026-11-02"))
        .as("the scenario the document reports is the text the run was given")
        .isEqualTo("2026-11-02");
  }

  @Test
  @DisplayName("ADV-2026-11-02-3: a scenario spelled like a YAML boolean stays a scenario")
  void aBooleanWordScenarioIsReadBackAsText() {
    assertThat(scenarioReadBack("off"))
        .as("a display name is a name, never a flag the reader resolves")
        .isEqualTo("off");
  }

  @Test
  @DisplayName("ADV-2026-11-02-3: a scenario spelled 'null' does not erase itself")
  void aNullWordScenarioIsNotReadBackAsAbsent() {
    assertThat(scenarioReadBack("null"))
        .as("the key is present, so the value may not be")
        .isEqualTo("null");
  }

  @Test
  @DisplayName("ADV-2026-11-02-3: a digit-only scenario is not read back as a number")
  void aDigitOnlyScenarioIsReadBackAsText() {
    assertThat(scenarioReadBack("1055")).as("an issue number is still a name").isEqualTo("1055");
  }

  /** The {@code scenario} field as the consumer's own YAML parser hands it over. */
  private static Object scenarioReadBack(String scenario) {
    var frontmatter =
        new FrontmatterBuilder().scenario(scenario).build(new DefaultTraceTree(List.of()));
    return Formats.frontmatterOf("FrontmatterBuilder", frontmatter).get("scenario");
  }
}
