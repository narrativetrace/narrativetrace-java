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
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.ProListing;
import ai.narrativetrace.skills.ProListingStatus;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentsMdRendererTest {

  private static final Skill SKILL =
      new Skill(
          "narrativetrace-x",
          "x",
          SkillClass.MECHANICAL,
          "A description with\na newline.",
          null,
          "sixty-seconds",
          List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
          List.of(),
          List.of(),
          CommandVocabulary.JAVA);

  @Test
  void rendersOneLineEntryPerSkillAndFoldsNewlines() {
    String section = AgentsMdRenderer.renderSection(List.of(SKILL), List.of());
    assertThat(section).startsWith(AgentsMdRenderer.BEGIN_MARKER + "\n");
    assertThat(section).endsWith(AgentsMdRenderer.END_MARKER + "\n");
    assertThat(section).contains("- `narrativetrace-x` — A description with a newline.\n");
  }

  @Test
  void rendersProListingsWithTheirStatusLabel() {
    ProListing listing =
        new ProListing(
            "narrativetrace-pro-example",
            "prompt",
            "delivers this",
            "needs that",
            "comes from there",
            ProListingStatus.IN_DEVELOPMENT,
            "in development");
    String section = AgentsMdRenderer.renderSection(List.of(), List.of(listing));
    assertThat(section)
        .contains("- `narrativetrace-pro-example` (Pro, in development) — delivers this\n");
  }

  @Test
  void spliceAppendsWhenNoMarkersExist() {
    String result = AgentsMdRenderer.splice("# Title\n", "SECTION\n");
    assertThat(result).isEqualTo("# Title\n\nSECTION\n");
  }

  @Test
  void spliceAppendsWithABlankLineWhenTheFileAlreadyEndsInNewline() {
    String result = AgentsMdRenderer.splice("# Title\ncontent\n", "SECTION\n");
    assertThat(result).isEqualTo("# Title\ncontent\n\nSECTION\n");
  }

  @Test
  void spliceAddsTwoNewlinesWhenTheFileHasNoTrailingNewline() {
    String result = AgentsMdRenderer.splice("# Title", "SECTION\n");
    assertThat(result).isEqualTo("# Title\n\nSECTION\n");
  }

  @Test
  void spliceReplacesAnExistingSection() {
    String original =
        "# Title\n\n"
            + AgentsMdRenderer.BEGIN_MARKER
            + "\nold content\n"
            + AgentsMdRenderer.END_MARKER
            + "\n\n## After\n";
    String replaced = AgentsMdRenderer.splice(original, "NEW SECTION\n");
    assertThat(replaced).isEqualTo("# Title\n\nNEW SECTION\n\n## After\n");
  }
}
