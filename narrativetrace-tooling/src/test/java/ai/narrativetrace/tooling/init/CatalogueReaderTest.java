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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The carrier index is read by hand, so the reader is tested against the REAL checked-in {@code
 * catalogue.json} as well as against hand-written malformed ones: a reader that only ever sees its
 * own fixtures proves nothing about the file that ships.
 */
class CatalogueReaderTest {

  private static final Path REAL_CATALOGUE =
      Path.of(System.getProperty("projectDir"))
          .resolve(
              "narrativetrace-skills/src/main/resources/META-INF/narrativetrace/skills/catalogue.json");

  private static String catalogue(String skills) {
    return "{\"runtime\": \"java\", \"skills\": [" + skills + "]}";
  }

  private static final String DOCTOR =
      """
      {"name": "narrativetrace-doctor", "description": "Diagnoses an install.",
       "agents": "agents/narrativetrace-doctor/SKILL.md",
       "claude": "claude/narrativetrace-doctor/SKILL.md"}
      """;

  @Test
  void readsTheRuntimeAndOneSkillFromACatalogue() {
    SkillCatalogue catalogue = CatalogueReader.read(catalogue(DOCTOR));

    assertThat(catalogue.runtime()).isEqualTo("java");
    assertThat(catalogue.skills())
        .containsExactly(
            new SkillEntry(
                "narrativetrace-doctor",
                "Diagnoses an install.",
                "agents/narrativetrace-doctor/SKILL.md",
                "claude/narrativetrace-doctor/SKILL.md"));
  }

  @Test
  void readsTheRealCheckedInCatalogue() throws IOException {
    SkillCatalogue catalogue = CatalogueReader.read(Files.readString(REAL_CATALOGUE));

    assertThat(catalogue.runtime()).isEqualTo("java");
    assertThat(catalogue.skills())
        .extracting(SkillEntry::name)
        .doesNotHaveDuplicates()
        .containsExactly(
            "narrativetrace-doctor",
            "add-narrative-tracing",
            "add-narrativetrace-clarity",
            "narrativetrace-feedback",
            "narrativetrace-verify",
            "narrativetrace-debug");
    assertThat(catalogue.skills())
        .allSatisfy(
            skill -> {
              assertThat(skill.agentsPath()).isEqualTo("agents/" + skill.name() + "/SKILL.md");
              assertThat(skill.claudePath()).isEqualTo("claude/" + skill.name() + "/SKILL.md");
              assertThat(skill.description()).isNotBlank();
            });
  }

  @Test
  void findsASkillByNameAndReportsAnUnknownOneAsEmpty() {
    SkillCatalogue catalogue = CatalogueReader.read(catalogue(DOCTOR));

    assertThat(catalogue.skill("narrativetrace-doctor")).isPresent();
    assertThat(catalogue.skill("narrativetrace-doctorx")).isEmpty();
  }

  // --- refusals: a malformed carrier is refused before any work, naming the entry --------------

  @Test
  void refusesACatalogueThatNamesTheSameSkillTwice() {
    assertThatThrownBy(() -> CatalogueReader.read(catalogue(DOCTOR + "," + DOCTOR)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names a skill twice")
        .hasMessageContaining("narrativetrace-doctor");
  }

  @Test
  void refusesASkillMissingAFlavourPathAndNamesIt() {
    String noClaude =
        """
        {"name": "add-narrative-tracing", "description": "d", "agents": "agents/a/SKILL.md"}
        """;

    assertThatThrownBy(() -> CatalogueReader.read(catalogue(noClaude)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("add-narrative-tracing")
        .hasMessageContaining("\"claude\"");
  }

  @Test
  void refusesASkillWhoseNameIsNotAString() {
    assertThatThrownBy(() -> CatalogueReader.read(catalogue("{\"name\": {}}")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("skill has no \"name\" string field");
  }

  @Test
  void refusesASkillThatIsNotAnObject() {
    assertThatThrownBy(() -> CatalogueReader.read(catalogue("\"narrativetrace-doctor\"")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("skill must be a JSON object");
  }

  @Test
  void refusesACatalogueWithoutASkillsArray() {
    assertThatThrownBy(() -> CatalogueReader.read("{\"runtime\": \"java\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("\"skills\" must be a JSON array");
  }

  @Test
  void refusesACatalogueWithoutARuntime() {
    assertThatThrownBy(() -> CatalogueReader.read("{\"skills\": []}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("catalogue has no \"runtime\" string field");
  }

  @Test
  void refusesACatalogueBuiltWithoutASkillList() {
    assertThatThrownBy(() -> new SkillCatalogue("java", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one skill");
  }

  @Test
  void refusesASkillEntryWithoutAFlavour() {
    SkillEntry entry = new SkillEntry("n", "d", "agents/n/SKILL.md", "claude/n/SKILL.md");

    assertThat(entry.pathFor(SkillFlavour.AGENTS)).isEqualTo("agents/n/SKILL.md");
    assertThat(entry.pathFor(SkillFlavour.CLAUDE)).isEqualTo("claude/n/SKILL.md");
    assertThatThrownBy(() -> entry.pathFor(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a flavour must be given");
  }

  @Test
  void eachFlavourKnowsWhereItInstalls() {
    assertThat(SkillFlavour.AGENTS.installRoot()).isEqualTo(".agents/skills");
    assertThat(SkillFlavour.CLAUDE.installRoot()).isEqualTo(".claude/skills");
  }

  @Test
  void refusesACatalogueThatCarriesNoSkillAtAll() {
    assertThatThrownBy(() -> CatalogueReader.read("{\"runtime\": \"java\", \"skills\": []}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one skill");
  }

  @Test
  void refusesACatalogueThatIsNotAnObjectAtAll() {
    assertThatThrownBy(() -> CatalogueReader.read("[]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("catalogue must be a JSON object");
  }

  @Test
  void refusesASkillWhoseDescriptionIsBlank() {
    String blank =
        """
        {"name": "n", "description": "  ", "agents": "a", "claude": "c"}
        """;

    assertThatThrownBy(() -> CatalogueReader.read(catalogue(blank)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("description must not be blank");
  }
}
