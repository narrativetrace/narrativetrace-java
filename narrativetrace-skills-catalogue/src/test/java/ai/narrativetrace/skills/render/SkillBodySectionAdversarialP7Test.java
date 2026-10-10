/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillSection;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Block separation of reference sections in the shared SKILL.md body. */
class SkillBodySectionAdversarialP7Test {

  @ParameterizedTest
  @ValueSource(strings = {"- a\n- b", "- a\n- b\n"})
  void aSectionIsFollowedByExactlyOneBlankLineWhetherOrNotItEndsWithANewline(String markdown) {
    var out = new StringBuilder();

    SkillBody.append(out, skill(new SkillSection("Shapes", markdown)), Path.of("."));

    assertThat(out.toString()).contains("## Shapes\n\n- a\n- b\n\n## Always\n\n- Do x (why)\n");
  }

  @Test
  void aSectionRendersBetweenTheStepsAndTheRuleSections() {
    var out = new StringBuilder();

    SkillBody.append(out, skill(new SkillSection("Shapes", "- a\n")), Path.of("."));

    var text = out.toString();
    assertThat(text.indexOf("## 1. Run it"))
        .isLessThan(text.indexOf("## Shapes"))
        .isLessThan(text.indexOf("## Always"));
  }

  private static Skill skill(SkillSection section) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.GUIDED,
        "a description",
        null,
        "sixty-seconds",
        List.of(new SkillStep("Run it", new StepBody.CommandStep(List.of()), "the suite is green")),
        List.of(new ReasonedRule("Do x", "why")),
        List.of(),
        List.of(),
        List.of(section));
  }
}
