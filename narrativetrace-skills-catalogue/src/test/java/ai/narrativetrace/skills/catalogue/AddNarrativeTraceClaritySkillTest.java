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
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import ai.narrativetrace.tooling.frameworks.Wiring;
import org.junit.jupiter.api.Test;

class AddNarrativeTraceClaritySkillTest {

  @Test
  void declaresTheRealScanAndArtifactChecks() {
    Skill skill = AddNarrativeTraceClaritySkill.build();

    assertThat(skill.canonicalName()).isEqualTo("add-narrativetrace-clarity");
    assertThat(skill.commandStrings())
        .contains(
            ClarityCommands.CLEAN_STATIC_SCAN,
            ClarityCommands.FIND_JSON_REPORT,
            ClarityCommands.FIND_MARKDOWN_REPORT);
    assertThat(skill.description())
        .contains("static naming report")
        .contains("narrativetrace-doctor");
    assertThat(skill.never())
        .extracting(rule -> rule.rule())
        .anyMatch(rule -> rule.contains("lower"))
        .anyMatch(rule -> rule.contains("glossary"));
  }

  /**
   * The doctor checks JUnit 4 now ({@code config.junit4-rule}), so the hand-off no longer tells an
   * agent to inspect JUnit 4 rules by hand because the doctor cannot.
   */
  @Test
  void theDoctorHandOffNamesTheJunit4CheckInsteadOfDisclaimingIt() {
    SkillStep handOff = stepTitled("Hand missing tracing or output setup to the doctor");

    assertThat(handOff.verify())
        .contains("config.junit4-rule")
        .doesNotContain("do not validate JUnit 4")
        .doesNotContain("Inspect JUnit 4 rules");
  }

  /**
   * The JUnit 4 linking step and the framework table's JUnit 4 row embed ONE compiled fixture: the
   * doctor's fix line and the clarity page cannot show two different ways to link the rules.
   */
  @Test
  void theJunit4StepEmbedsTheFrameworkTablesJunit4Fixture() {
    SkillStep linking = stepTitled("For JUnit 4, link the class rule and per-test rule");
    Wiring.Snippet row = (Wiring.Snippet) FrameworkTable.row("junit4").orElseThrow().wiring();

    assertThat(((StepBody.SnippetStep) linking.body()).path()).isEqualTo(row.fixture());
  }

  /** The plugin line carries the placeholder, and the lookup step sits right before it. */
  @Test
  void resolvesTheNewestReleaseRightBeforeApplyingThePluginAndPinsNoVersion() {
    var steps = AddNarrativeTraceClaritySkill.build().steps();
    int apply = steps.indexOf(stepTitled("Apply the plugin only when the project needs it"));

    assertThat(steps.get(apply - 1).title()).isEqualTo(ReleaseLookup.step().title());
    assertThat(((StepBody.CodeStep) steps.get(apply).body()).code())
        .contains("id(\"ai.narrativetrace\") version \"<version>\"")
        .doesNotContainPattern("version \"\\d");
  }

  private static SkillStep stepTitled(String title) {
    return AddNarrativeTraceClaritySkill.build().steps().stream()
        .filter(step -> step.title().equals(title))
        .findFirst()
        .orElseThrow();
  }
}
