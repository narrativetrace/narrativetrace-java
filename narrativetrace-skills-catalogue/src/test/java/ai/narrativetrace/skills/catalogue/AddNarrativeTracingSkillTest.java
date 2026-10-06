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
import ai.narrativetrace.skills.SkillStep;
import org.junit.jupiter.api.Test;

class AddNarrativeTracingSkillTest {

  @Test
  void endsBySettingUpTheSkillsForTheNextSession() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(skill.commandStrings()).contains(InstallerCommands.PREVIEW_INSTALL);
    assertThat(last.title()).isEqualTo("Install the skills for next time");
    assertThat(last.verify()).isEqualTo(InstallerCommands.VERIFY_PREVIEW_WROTE_NOTHING);
  }

  /**
   * The installer is previewed, never applied blind: the diff goes to a person first. A skill that
   * writes into {@code AGENTS.md} and {@code .agents/skills/} on its own initiative is exactly the
   * "never a postinstall hook" line the installer's whole design is built around.
   */
  @Test
  void neverRunsTheInstallerWithoutShowingTheDiffFirst() {
    Skill skill = AddNarrativeTracingSkill.build();

    assertThat(skill.commandStrings())
        .as("the applying form is a person's decision, never a step")
        .doesNotContain("./gradlew narrativetraceInit");
    assertThat(skill.never())
        .extracting(rule -> rule.rule())
        .anyMatch(rule -> rule.contains("diff"));
  }

  @Test
  void stillEndsEveryRunAtTheDoctor() {
    Skill skill = AddNarrativeTracingSkill.build();

    assertThat(skill.commandStrings()).contains(DoctorCommands.RUN_DOCTOR_GRADLE);
    assertThat(skill.never()).extracting(rule -> rule.rule()).anyMatch(r -> r.contains("doctor"));
  }
}
