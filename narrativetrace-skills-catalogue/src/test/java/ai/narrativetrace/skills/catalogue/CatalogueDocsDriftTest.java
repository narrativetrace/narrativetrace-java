/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every catalogue skill is named on every public page that lists the skills: {@code llms.txt}'s
 * "Agent skills" section, {@code agent-skills.md} and each of its translations.
 *
 * <p>INTENT: the rendered pages and {@code AGENTS.md} are drift-checked against the catalogue by
 * {@code RenderDriftTest}, but these pages are written by hand; without this test a skill added to
 * {@link CatalogueIndex#ALL} ships with {@code check} green and no page telling a reader it exists.
 */
class CatalogueDocsDriftTest {

  private static final Path DOCS = Path.of(System.getProperty("projectDir"), "documentation");

  private static final List<String> SKILL_PAGES =
      List.of(
          "agent-skills.md",
          "es/habilidades-de-agente.md",
          "pt-BR/habilidades-de-agente.md",
          "zh-CN/智能体技能.md");

  static List<Skill> skills() {
    return CatalogueIndex.ALL;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void llmsTxtListsTheSkillInItsAgentSkillsSection(Skill skill) throws IOException {
    String llms = Files.readString(DOCS.resolve("llms.txt"));
    String section = llms.substring(llms.indexOf("## Agent skills"));
    section = section.substring(0, section.indexOf("\n## ", 1));

    assertThat(section).contains("- `" + skill.canonicalName() + "` — ");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void everyAgentSkillsPageNamesTheSkillInItsList(Skill skill) throws IOException {
    for (String page : SKILL_PAGES) {
      assertThat(Files.readString(DOCS.resolve(page)))
          .as(page)
          .contains("- **`" + skill.canonicalName() + "`**");
    }
  }
}
