/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The verify loop as a typed skill: the cost rule, the intent first, structure first, the pin. */
class NarrativeTraceVerifySkillTest {

  private final Skill skill = NarrativeTraceVerifySkill.build();

  @Test
  void isNamedForTheCatalogueAndShipsInIt() {
    assertThat(skill.canonicalName()).isEqualTo("narrativetrace-verify");
    assertThat(CatalogueIndex.ALL).contains(skill);
  }

  @Test
  void theFirstStepDecidesWhetherToTraceAndSaysSo() {
    SkillStep first = skill.steps().get(0);

    assertThat(first.title()).isEqualTo("Decide whether to trace, and say so");
    assertThat(first.verify())
        .contains("skipping narrativetrace-verify:")
        .contains("a pure function")
        .contains("a skip ends the skill here")
        .contains("dozens of lines");
  }

  @Test
  void theIntentIsWrittenBeforeAnythingRuns() {
    SkillStep second = skill.steps().get(1);

    assertThat(second.title()).isEqualTo("Write the intent down before running anything");
    assertThat(second.body()).isEqualTo(new StepBody.CommandStep(List.of()));
    assertThat(second.verify()).contains("before the first traced run").contains("three to six");
  }

  @Test
  void theRunStepEmbedsTheCompiledFixtureTestAndRunsIt() {
    SkillStep run = step("Run the smallest real path with tracing on");

    assertThat(run.body())
        .isEqualTo(
            new StepBody.SnippetStep(
                "java", "sixty-seconds/src/test/java/com/example/orders/PlaceOrderFlowTest.java"));
    assertThat(run.verify()).isEqualTo(VerifyCommands.RUN_THE_PATH);
  }

  @Test
  void theStructuralTraceIsReadBeforeAnyValue() {
    var titles = skill.steps().stream().map(SkillStep::title).toList();

    assertThat(titles.indexOf("Read the structural trace first, against the intent"))
        .isLessThan(titles.indexOf("Open values on the span that looks wrong, and only there"));
  }

  @Test
  void valuesAndTheFixAreConditionalOnAMismatch() {
    assertThat(step("Open values on the span that looks wrong, and only there").condition())
        .contains("only when");
    assertThat(step("Fix, re-run, read again").condition()).contains("only when");
    assertThat(step("Turn approval mode on").condition()).contains("approval mode is off");
  }

  @Test
  void aTraceFromARunBeforeTheIntentDoesNotCount() {
    assertThat(step("Run the smallest real path with tracing on").condition())
        .contains("a trace from a run made before the Intent was written does not count");
  }

  @Test
  void theQuestionIsTheLastLineOfTheReply() {
    assertThat(step("Ask once whether to pin it, then stop the turn").verify())
        .contains("everything else — the report, every caveat — goes before the question")
        .endsWith("the question is the reply's last line.");
  }

  @Test
  void everyStepCarriesItsOwnVerify() {
    assertThat(skill.steps()).allMatch(step -> step.verify() != null && !step.verify().isBlank());
  }

  @Test
  void thePinShowsTheWholeArtifactAsksOnceAndPromotesOnlyWhatWasShown() {
    assertThat(step("Run the suite in approval mode and show every .received.nt").verify())
        .contains(ApprovalGate.verifyShownWhole("each .received.nt that run wrote"))
        .contains("fails on purpose");
    assertThat(step("Ask once whether to pin it, then stop the turn").verify())
        .startsWith(ApprovalGate.verifyAskedThenStopped(""))
        .contains("Do not run approveNarratives before the user says yes");
    assertThat(step("Promote what was shown, and nothing else").body())
        .isEqualTo(new StepBody.CommandStep(List.of(VerifyCommands.APPROVE)));
  }

  @Test
  void theGateRulesAreTheFeedbackSkillsRulesVerbatim() {
    assertThat(skill.never())
        .contains(
            ApprovalGate.neverInTheTurnThatAsked("promote a baseline"),
            ApprovalGate.neverEditAfterShowing(".received.nt", "promoted", "run is rendered"));
    assertThat(skill.always().stream().map(ReasonedRule::rule))
        .contains("Show the whole .received.nt before asking anything");
  }

  @Test
  void theReportCitesSpanIds() {
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(last.title()).isEqualTo("Report what the trace showed");
    assertThat(last.verify()).contains("span id").contains("a claim without an id is not a claim");
  }

  @Test
  void rendersTheSharedReadingSections() {
    assertThat(skill.sections()).containsExactly(TraceReading.FLAVOURS, TraceReading.SHAPES);
  }

  @Test
  void preApprovesNoTool() {
    assertThat(skill.allowedTools()).isEmpty();
  }

  @Test
  void theDescriptionCarriesTheTriggerPhrases() {
    assertThat(skill.description())
        .contains("'verify this change with the trace'")
        .contains("'check what the code actually did'")
        .contains("'pin this flow as a baseline'");
    assertThat(skill.descriptionFitsBudget()).isTrue();
  }

  private SkillStep step(String title) {
    return skill.steps().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
  }
}
