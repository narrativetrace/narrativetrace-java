/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.render.ClaudeSkillRenderer;
import org.junit.jupiter.api.Test;

/**
 * Both install skills name TestNG the way they name JUnit 4: an agent applying a skill to a TestNG
 * project does what the Gradle plugin supports ({@code testFramework.set("testng")}) and needs no
 * change in the tests, because the listener attaches itself through TestNG's service loader.
 */
class TestNgBranchTest {

  private static String clarity() {
    return ClaudeSkillRenderer.render(AddNarrativeTraceClaritySkill.build());
  }

  private static String tracing() {
    return ClaudeSkillRenderer.render(AddNarrativeTracingSkill.build());
  }

  @Test
  void theClarityStepForTestNgKeepsItsRunnerAndLetsTheListenerAttachItself() {
    assertThat(clarity())
        .contains("For TestNG, keep its runner and let the listener attach itself")
        .contains("testFramework.set(\"testng\")")
        .contains("narrativetrace-testng")
        .contains("useTestNG()")
        .contains("no @Listeners")
        .contains("NarrativeTraceListener.context()");
  }

  @Test
  void theClarityInspectionAndPluginSentencesAndDoctorHandOffNameTestNg() {
    assertThat(clarity())
        .contains("test framework (JUnit 4, JUnit 5 or TestNG)")
        .contains("testFramework.set(\"testng\")")
        .contains("config.testng-listener");
  }

  @Test
  void theTracingSkillOffersTheTestNgModuleWithoutNamingTheFramework() {
    // The tracing page names no framework (the doctor's table is the oracle), only the module.
    assertThat(tracing())
        .contains("narrativetrace-testng for a project whose tests already run on");
  }
}
