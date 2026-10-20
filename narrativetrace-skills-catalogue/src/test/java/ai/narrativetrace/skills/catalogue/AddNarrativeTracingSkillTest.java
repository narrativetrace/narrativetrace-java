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

  /**
   * Step 3's condition (Phase 6, D2): a project that already has tests gets its first trace from
   * one EXISTING test run through the test integration, never a new demo main and never a test this
   * page adds itself — the files that step writes are the ones narrativetrace-doctor, -verify,
   * -debug and -explain all read. A project with no tests keeps the console-app path, now saying
   * plainly that the script itself writes nothing to disk.
   */
  @Test
  void firstTraceBranchesOnWhetherTheProjectAlreadyHasTests() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep step = skill.steps().get(2);

    assertThat(step.title()).isEqualTo("First trace: wrap, call, render, run");
    assertThat(step.condition())
        .contains("if the project already has tests")
        .contains("narrativetrace-junit5")
        .contains("narrativetrace-junit4")
        .contains(".md and .nt")
        .contains("build/narrativetrace/")
        .contains("narrativetrace-doctor")
        .contains("narrativetrace-verify")
        .contains("narrativetrace-debug")
        .contains("narrativetrace-explain")
        .contains("the step is done when that test's files exist")
        .contains("nothing is written to disk by this script")
        .contains("this page never adds a test runner or a test to a project that has none")
        // the existing entry-point branch stays, for the no-tests case
        .contains("do not add a demo main");
  }

  /**
   * The third premise (B-92 follow-up): {@link AddNarrativeTracingSkill#FIRST_TRACE_HAS_TESTS}
   * covers "has tests" and the no-tests branch covers "none" — neither says what happens when the
   * project's tests run on a runner the doctor has no test integration for. The page keeps that
   * runner (it never swaps it) and takes the console path below, naming no framework: {@link
   * #theRenderedPageNamesNoFrameworkTheDoctorChecks} stays green.
   */
  @Test
  void conditionNamesTheRunnerWithoutIntegrationPremise() {
    Skill skill = AddNarrativeTracingSkill.build();
    SkillStep step = skill.steps().get(2);

    assertThat(step.condition())
        .contains("the doctor reports a test integration for that runner")
        .contains("a runner the doctor reports no integration for")
        .contains("keeps that runner")
        .contains("takes the console path below");
  }
}
