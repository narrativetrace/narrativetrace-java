/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogueJsonRendererTest {

  private static Skill skill(String name, String description) {
    return new Skill(
        name,
        SkillClass.MECHANICAL,
        description,
        null,
        "fixtures/whatever",
        List.of(
            new SkillStep(
                "Do it", new StepBody.CommandStep(List.of("./gradlew help")), "it exits 0")),
        List.of(),
        List.of(),
        List.of("Bash"));
  }

  @Test
  void namesTheRuntimeAndEverySkillExactlyOnce() {
    String json = CatalogueJsonRenderer.render(CatalogueIndex.ALL);

    assertThat(json).startsWith("{\n  \"runtime\": \"java\",\n");
    for (Skill skill : CatalogueIndex.ALL) {
      assertThat(json.split("\"name\": \"" + skill.canonicalName() + "\"", -1))
          .as("%s appears exactly once", skill.canonicalName())
          .hasSize(2);
    }
  }

  @Test
  void carriesBothFlavourPathsForEverySkill() {
    String json = CatalogueJsonRenderer.render(CatalogueIndex.ALL);

    for (Skill skill : CatalogueIndex.ALL) {
      assertThat(json).contains("\"agents\": \"" + RenderPaths.carrierAgentsResource(skill) + "\"");
      assertThat(json).contains("\"claude\": \"" + RenderPaths.carrierClaudeResource(skill) + "\"");
    }
  }

  /** D2: the jar's own coordinate is the stamp — a literal here would drift every release. */
  @Test
  void carriesNoVersionLiteral() {
    assertThat(CatalogueJsonRenderer.render(CatalogueIndex.ALL))
        .doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void endsWithExactlyOneTrailingNewline() {
    String json = CatalogueJsonRenderer.render(CatalogueIndex.ALL);

    assertThat(json).endsWith("}\n").doesNotEndWith("\n\n");
  }

  @Test
  void escapesQuotesBackslashesAndControlCharactersInADescription() {
    String bell = String.valueOf((char) 7);
    String json =
        CatalogueJsonRenderer.render(
            List.of(
                skill("quoted", "a \"quote\", a \\ slash,\na newline and a " + bell + " bell")));

    assertThat(json)
        .contains(
            "\"description\": \"a \\\"quote\\\", a \\\\ slash,\\na newline and a \\u0007 bell\"");
  }

  @Test
  void rendersAnEmptyCatalogueAsAnEmptyArray() {
    assertThat(CatalogueJsonRenderer.render(List.of()))
        .isEqualTo("{\n  \"runtime\": \"java\",\n  \"skills\": []\n}\n");
  }

  @Test
  void separatesSkillObjectsWithACommaAndNeverATrailingOne() {
    String json = CatalogueJsonRenderer.render(List.of(skill("a", "first"), skill("b", "second")));

    assertThat(json).contains("    },\n    {").doesNotContain(",\n  ]");
  }
}
