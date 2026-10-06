/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexSkillRendererTest {

  private static Skill skill(String whenToUse) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        "A description with a \"quote\".",
        whenToUse,
        "sixty-seconds",
        List.of(new SkillStep("Step one", new StepBody.CommandStep(List.of("git status")), null)),
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  @Test
  void frontmatterCarriesOnlyNameAndDescription() {
    String rendered = CodexSkillRenderer.render(skill(null));

    assertThat(rendered)
        .isEqualTo(
            "---\n"
                + "name: narrativetrace-x\n"
                + "description: \"A description with a \\\"quote\\\".\"\n"
                + "---\n\n"
                + "# narrativetrace-x\n\n"
                + "## 1. Step one\n\n"
                + "```bash\n"
                + "git status\n"
                + "```\n\n");
  }

  @Test
  void neverEmitsWhenToUseOrAllowedTools() {
    String rendered = CodexSkillRenderer.render(skill("When to use it."));

    assertThat(rendered).doesNotContain("when_to_use:").doesNotContain("allowed-tools:");
  }

  @Test
  void sharesTheSamePageBodyAsTheClaudePage() {
    Skill skill = skill(null);
    String claudeRendered = ClaudeSkillRenderer.render(skill);
    String codexRendered = CodexSkillRenderer.render(skill);

    assertThat(bodyOf(codexRendered, skill.canonicalName()))
        .isEqualTo(bodyOf(claudeRendered, skill.canonicalName()));
  }

  private static String bodyOf(String rendered, String canonicalName) {
    return rendered.substring(rendered.indexOf("# " + canonicalName));
  }
}
