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

/** The explain loop as a typed skill: decide, choose, run, read the shape, explain by span id. */
class NarrativeTraceExplainSkillTest {

  private final Skill skill = NarrativeTraceExplainSkill.build();

  @Test
  void shipsInTheCatalogueAfterTheDebugSkill() {
    var names = CatalogueIndex.ALL.stream().map(Skill::canonicalName).toList();

    assertThat(CatalogueIndex.ALL).contains(skill);
    assertThat(skill.canonicalName()).isEqualTo("narrativetrace-explain");
    assertThat(names.indexOf("narrativetrace-explain"))
        .isEqualTo(names.indexOf("narrativetrace-debug") + 1);
  }

  @Test
  void theFirstStepDecidesWhetherTheSourceAlreadyAnswersTheQuestion() {
    SkillStep first = skill.steps().get(0);

    assertThat(first.title()).isEqualTo("Decide whether the source already answers it, and say so");
    assertThat(first.body()).isEqualTo(new StepBody.CommandStep(List.of()));
    assertThat(first.verify())
        .contains("before anything is installed or run")
        .contains("'reading the source:")
        .contains("'tracing:")
        .contains("no install and no run")
        .contains("the skill ends there");
  }

  @Test
  void theCostRuleIsAnAlwaysRuleCarryingItsOwnReason() {
    assertThat(skill.always()).contains(NarrativeTraceExplainSkill.FROM_THE_SOURCE);
    assertThat(NarrativeTraceExplainSkill.FROM_THE_SOURCE.rule())
        .isEqualTo("Answer from the source when ten lines of source answer the question");
    assertThat(NarrativeTraceExplainSkill.FROM_THE_SOURCE.reason())
        .contains("cost the user time and tokens")
        .contains("where the path is not obvious");
  }

  @Test
  void oneScenarioIsChosenAndANewOneWrittenOnlyWhereThereIsNone() {
    SkillStep scenario = step("Choose the one scenario that already exercises the flow");
    var titles = titles();

    assertThat(scenario.verify())
        .contains("names ONE scenario")
        .contains("an existing test")
        .contains("written only where")
        .contains("Two scenarios is two traces");
    assertThat(titles.indexOf(scenario.title())).isEqualTo(1);
  }

  @Test
  void anUninstalledProjectIsRoutedThroughTheInstallSkillBeforeTheRun() {
    SkillStep install = step("Install tracing first when the project has none");
    var titles = titles();

    assertThat(install.condition())
        .contains("only when the project has no NarrativeTrace at all")
        .contains("go straight to the run");
    assertThat(install.verify())
        .contains("add-narrative-tracing")
        .contains("resumed at the run below")
        .contains("does not refactor");
    assertThat(titles.indexOf(install.title()))
        .isLessThan(titles.indexOf("Run that one scenario with tracing on"));
  }

  @Test
  void theRunDrivesTheScenarioAsFoundAndRoutesElsewhereWhenThereIsNone() {
    SkillStep run = step("Run that one scenario with tracing on");

    assertThat(run.body())
        .isEqualTo(new StepBody.CommandStep(List.of(ExplainCommands.RUN_THE_SCENARIO)));
    assertThat(run.verify())
        .contains("wrote a trace of its own")
        .contains("a run that writes nothing is the failure below")
        .contains("ran as it was found");
    assertThat(run.condition())
        .contains("run that one, as it is")
        .contains("add-narrative-tracing's own first-trace step");
    assertThat(run.failure())
        .singleElement()
        .satisfies(note -> assertThat(note.fix()).contains("narrativetrace-doctor"));
  }

  @Test
  void theStructuralTraceIsReadWholeAndInOrderBeforeAnyValue() {
    SkillStep shape = step("Read the structural trace first, in the order it ran");
    SkillStep values = step("Open values only where the question needs one");
    var titles = titles();

    assertThat(shape.body())
        .isEqualTo(new StepBody.CommandStep(List.of(VerifyCommands.FIND_STRUCTURAL)));
    assertThat(shape.verify())
        .contains("read whole before any value")
        .contains("by span id")
        .contains("which branch");
    assertThat(values.body())
        .isEqualTo(new StepBody.CommandStep(List.of(VerifyCommands.FIND_NARRATIVES)));
    assertThat(values.condition())
        .isEqualTo(NarrativeTraceExplainSkill.ONLY_WHAT_THE_QUESTION_NEEDS);
    assertThat(values.verify()).contains("not the whole file").contains("[REDACTED]");
    assertThat(titles.indexOf(values.title())).isEqualTo(titles.indexOf(shape.title()) + 1);
  }

  @Test
  void aDefectOrAChangeInProgressIsHandedOffInsteadOfFixedHere() {
    SkillStep handOff = step("Hand off when it stops being an explanation");
    var titles = titles();

    assertThat(handOff.condition())
        .contains("only when the run shows something wrong")
        .contains("a change of this session's own");
    assertThat(handOff.verify())
        .contains("narrativetrace-debug")
        .contains("narrativetrace-verify")
        .contains("fixes nothing itself");
    assertThat(titles.indexOf(handOff.title()))
        .isGreaterThan(titles.indexOf("Read the structural trace first, in the order it ran"))
        .isLessThan(titles.indexOf("Explain the run by span id, and mark what you inferred"));
  }

  @Test
  void theLastStepExplainsBySpanIdAndNamesWhatWasNotObserved() {
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(last.title()).isEqualTo("Explain the run by span id, and mark what you inferred");
    assertThat(last.verify())
        .contains("in the user's own words")
        .contains("citing the span id it rests on")
        .contains("a claim without an id is not a claim")
        .contains("marked as an inference")
        .contains("what was NOT observed")
        .contains("the branches this run did not take");
  }

  @Test
  void rendersTheReadingSectionsTheVerifyAndDebugSkillsRender() {
    assertThat(skill.sections())
        .containsExactly(TraceReading.FLAVOURS, TraceReading.SHAPES)
        .isEqualTo(NarrativeTraceDebugSkill.build().sections());
  }

  @Test
  void preApprovesNoTool() {
    assertThat(skill.allowedTools()).isEmpty();
  }

  @Test
  void theRulesForbidInventingObservationsAndChangingTheCodeToExplainIt() {
    var always = skill.always().stream().map(ReasonedRule::rule).toList();
    var never = skill.never().stream().map(ReasonedRule::rule).toList();

    assertThat(always)
        .contains(
            "Cite a span id for every claim about the trace",
            "Separate what the trace showed from what you inferred");
    assertThat(never)
        .containsExactly(
            "Never present an unobserved call as something the trace showed",
            "Never change the code in order to explain it",
            "Never turn redaction off to see more");
    assertThat(skill.never()).contains(TraceReading.NEVER_REDACTION_OFF);
  }

  @Test
  void theDescriptionCarriesTheQuestionsAUserAsksAndFitsTheBudget() {
    assertThat(skill.description())
        .contains("'show me what my application actually does'")
        .contains("'walk me through what happens when a user checks out'")
        .contains("'I inherited this service, how does an order flow'")
        .contains("'what does this endpoint call'")
        .contains("nothing broken and no change needing verification")
        .contains("BEFORE reading source files")
        .contains("add-narrative-tracing")
        .contains("narrativetrace-debug")
        .contains("narrativetrace-verify");
    assertThat(skill.whenToUse()).contains("Non-obvious triggers");
    assertThat(skill.descriptionFitsBudget()).isTrue();
  }

  @Test
  void itIsAReadingLoopSoItNamesNoCommandThatWritesAnything() {
    assertThat(skill.commandStrings())
        .containsExactly(
            ExplainCommands.RUN_THE_SCENARIO,
            VerifyCommands.FIND_STRUCTURAL,
            VerifyCommands.FIND_NARRATIVES);
    assertThat(skill.commandStrings())
        .noneMatch(command -> command.contains("approveNarratives"))
        .noneMatch(command -> command.contains("narrativetraceInit"));
  }

  @Test
  void everyStepCarriesItsOwnVerify() {
    assertThat(skill.steps()).allMatch(step -> step.verify() != null && !step.verify().isBlank());
  }

  private List<String> titles() {
    return skill.steps().stream().map(SkillStep::title).toList();
  }

  private SkillStep step(String title) {
    return skill.steps().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
  }
}
