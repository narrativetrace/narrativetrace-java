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
import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.render.ClaudeSkillRenderer;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AddNarrativeTracingSkillTest {

  @Test
  void endsBySettingUpTheSkillsForTheNextSession() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(skill.commandStrings()).contains(InstallerCommands.PREVIEW_INSTALL);
    assertThat(last.title()).isEqualTo("Install the skills for next time");
    assertThat(last.verify()).startsWith(InstallerCommands.VERIFY_PREVIEW_WROTE_NOTHING);
  }

  @Test
  void theLastStepPointsTheNextSessionAtTheVerifySkill() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(last.verify())
        .contains("the next session verifies with narrativetrace-verify")
        .contains("reads the trace before it reports");
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

  /**
   * Phase 6, D2 as amended: right after the core install, ONE step hands framework wiring to the
   * doctor — run it, apply every {@code config.<framework>-*} fix in order, leave alone a framework
   * with no integration shipped. The doctor is the run-time oracle for the version installed.
   */
  @Test
  void wiresTheFrameworksTheDoctorReportsRightAfterTheInstall() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep step = skill.steps().get(1);

    assertThat(skill.steps().get(0).title()).isEqualTo("Install with the real toolchain");
    assertThat(step.title()).isEqualTo("Wire the frameworks this project already uses");
    assertThat(step.body())
        .isEqualTo(new StepBody.CommandStep(java.util.List.of(DoctorCommands.RUN_DOCTOR_GRADLE)));
    assertThat(step.verify())
        .isEqualTo(DoctorCommands.VERIFY_FRAMEWORK_FIXES_APPLIED)
        .contains("config.<framework>-*")
        .contains("in order")
        .contains("no integration shipped");
    assertThat(step.failure()).contains(AddNarrativeTracingSkill.DOCTOR_TASK_NOT_FOUND);
  }

  /**
   * The page lists NO frameworks: which frameworks exist, and how each is wired, is the installed
   * doctor's answer, never text that goes stale on a skill page.
   */
  @Test
  void theRenderedPageNamesNoFrameworkTheDoctorChecks() {
    String page =
        ClaudeSkillRenderer.render(
            AddNarrativeTracingSkill.build(), Path.of(System.getProperty("projectDir", ".")));
    FrameworkTable.rowsWithWiringChecks()
        .forEach(row -> assertThat(page).as(row.id()).doesNotContain(row.name()));
  }
}
