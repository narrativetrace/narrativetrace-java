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

/** The debug loop as a typed skill: reproduce with values, localize by span, fix, pin, report. */
class NarrativeTraceDebugSkillTest {

  private final Skill skill = NarrativeTraceDebugSkill.build();

  @Test
  void shipsInTheCatalogueAfterTheVerifySkill() {
    var names = CatalogueIndex.ALL.stream().map(Skill::canonicalName).toList();

    assertThat(CatalogueIndex.ALL).contains(skill);
    assertThat(names.indexOf("narrativetrace-debug"))
        .isEqualTo(names.indexOf("narrativetrace-verify") + 1);
  }

  @Test
  void theFirstStepReproducesTheSymptomWithTheCompiledFixtureTest() {
    SkillStep first = skill.steps().get(0);

    assertThat(skill.canonicalName()).isEqualTo("narrativetrace-debug");
    assertThat(first.title()).isEqualTo("Reproduce the symptom with tracing on");
    assertThat(first.body())
        .isEqualTo(
            new StepBody.SnippetStep(
                "java", "sixty-seconds/src/test/java/com/example/orders/PlaceOrderFlowTest.java"));
    assertThat(first.verify()).isEqualTo(DebugCommands.REPRODUCE);
  }

  @Test
  void theSymptomMustShowInTheValuesOrTheLoopStops() {
    SkillStep second = skill.steps().get(1);

    assertThat(second.title()).isEqualTo("Find the symptom in the values");
    assertThat(second.body())
        .isEqualTo(new StepBody.CommandStep(List.of(VerifyCommands.FIND_NARRATIVES)));
    assertThat(second.verify())
        .contains("the reported value")
        .contains("does not reproduce")
        .contains("stops here");
  }

  @Test
  void localizingNamesTheFirstDivergingSpanByIdBeforeAnyFix() {
    SkillStep localize = step("Localize by reading: name the first span where a value diverges");

    assertThat(localize.body()).isEqualTo(new StepBody.CommandStep(List.of()));
    assertThat(localize.verify())
        .contains("by its id")
        .contains("the boundary")
        .contains("before any code is changed")
        .contains("not by stepping");
  }

  @Test
  void acrossThreadsTheSequenceDiagramIsReadBeforeTheSpan() {
    SkillStep diagram = step("Across threads, read the sequence diagram first");
    var titles = skill.steps().stream().map(SkillStep::title).toList();

    assertThat(diagram.condition()).contains("only when").contains("thread");
    assertThat(diagram.body())
        .isEqualTo(new StepBody.CommandStep(List.of(DebugCommands.FIND_DIAGRAMS)));
    assertThat(diagram.verify()).contains("span id").contains("before");
    assertThat(titles.indexOf(diagram.title()))
        .isLessThan(
            titles.indexOf("Localize by reading: name the first span where a value diverges"));
  }

  @Test
  void bisectingNarrowsToTheSubTreeUnderTheDivergingSpanNeverByRedacting() {
    SkillStep bisect = step("Bisect by span, not by file");
    var titles = skill.steps().stream().map(SkillStep::title).toList();

    assertThat(titles.indexOf(bisect.title()))
        .isEqualTo(
            titles.indexOf("Localize by reading: name the first span where a value diverges") + 1);
    assertThat(bisect.condition()).contains("only when");
    assertThat(bisect.body()).isEqualTo(new StepBody.CommandStep(List.of(DebugCommands.REPRODUCE)));
    assertThat(bisect.verify())
        .contains("sub-tree")
        .contains("NarrativeTraceProxy.trace")
        .contains("never @NotTraced");
  }

  @Test
  void theFixIsInTheDivergingSpanAndTheStructuralDeltaShowsNothingElseMoved() {
    SkillStep fix = step("Fix it in the diverging span, re-run the same input, read the same span");
    SkillStep delta = step("Check that nothing else moved");
    var titles = skill.steps().stream().map(SkillStep::title).toList();

    assertThat(fix.body()).isEqualTo(new StepBody.CommandStep(List.of(DebugCommands.REPRODUCE)));
    assertThat(fix.verify())
        .contains("the same id")
        .contains("the code of that span")
        .contains("silences the symptom");
    assertThat(delta.body())
        .isEqualTo(new StepBody.CommandStep(List.of(VerifyCommands.FIND_STRUCTURAL)));
    assertThat(delta.verify())
        .contains("line by line")
        .contains("a red run writes no .nt")
        .contains("the reproduction's .md");
    assertThat(titles.indexOf(delta.title())).isEqualTo(titles.indexOf(fix.title()) + 1);
    assertThat(titles.indexOf(fix.title()))
        .isGreaterThan(titles.indexOf("Bisect by span, not by file"));
  }

  @Test
  void theRegressionIsPinnedAsATestPlusTheSharedBaselinePin() {
    SkillStep keep = step("Keep the reproduction as the regression test");
    int at = skill.steps().indexOf(keep);
    List<SkillStep> pin = BaselinePin.steps();

    assertThat(at).isEqualTo(skill.steps().indexOf(step("Check that nothing else moved")) + 1);
    assertThat(keep.verify())
        .contains("asserts the value")
        .contains("fails when the fix is undone");
    assertThat(skill.steps().subList(at + 1, at + 1 + pin.size())).isEqualTo(pin);
  }

  @Test
  void theReportNamesTheRootCauseBySpanIdAndIsTheLastStep() {
    SkillStep last = skill.steps().get(skill.steps().size() - 1);

    assertThat(last.title()).isEqualTo("Report the root cause as the trace showed it");
    assertThat(last.verify())
        .contains("span id")
        .contains("the value")
        .contains("a claim without an id is not a claim")
        .contains("before the pin question")
        .contains("the closing reply after the promotion names the span id again");
  }

  @Test
  void aDefectInNarrativeTraceItselfIsHandedOffBeforeAnythingIsFixed() {
    SkillStep handOff = step("Hand a defect in NarrativeTrace itself to narrativetrace-feedback");
    var titles = skill.steps().stream().map(SkillStep::title).toList();

    assertThat(handOff.condition()).contains("only when").contains("NarrativeTrace");
    assertThat(handOff.verify()).contains("narrativetrace-feedback").contains("never a value");
    assertThat(titles.indexOf(handOff.title()))
        .isGreaterThan(titles.indexOf("Bisect by span, not by file"))
        .isLessThan(
            titles.indexOf(
                "Fix it in the diverging span, re-run the same input, read the same span"));
  }

  @Test
  void rendersTheReadingSectionsTheVerifySkillRenders() {
    assertThat(skill.sections())
        .containsExactly(TraceReading.FLAVOURS, TraceReading.SHAPES)
        .isEqualTo(NarrativeTraceVerifySkill.build().sections());
  }

  @Test
  void preApprovesNoToolBecauseItPromotesABaseline() {
    assertThat(skill.allowedTools()).isEmpty();
  }

  @Test
  void theGateRulesAreTheSharedApprovalGateAndTheDebugRulesForbidGuessing() {
    var never = skill.never().stream().map(ReasonedRule::rule).toList();

    assertThat(skill.never())
        .contains(
            ApprovalGate.neverInTheTurnThatAsked("promote a baseline"),
            ApprovalGate.neverEditAfterShowing(".received.nt", "promoted", "run is rendered"));
    assertThat(skill.always().stream().map(ReasonedRule::rule))
        .contains(
            "Show the whole .received.nt before asking anything",
            "Cite a span id for every claim about the trace",
            "Narrow to one span before reading its values");
    assertThat(never)
        .contains(
            "Never change code before the diverging span is named",
            "Never make the symptom go away somewhere other than the diverging span",
            "Never turn redaction off to see more");
  }

  @Test
  void theDescriptionCarriesTheTriggerPhrasesAndFitsTheBudget() {
    assertThat(skill.description())
        .contains("'debug this with the trace'")
        .contains("'find where this value goes wrong'")
        .contains("'why is this result wrong'")
        .contains("narrativetrace-feedback");
    assertThat(skill.whenToUse()).contains("Non-obvious triggers");
    assertThat(skill.descriptionFitsBudget()).isTrue();
  }

  @Test
  void theReproductionUsesTheReportedInputAndSaysWhatToDoWhenNoTraceAppears() {
    SkillStep first = skill.steps().get(0);

    assertThat(first.condition())
        .contains("the input from the symptom")
        .contains("otherwise write the smallest one");
    assertThat(first.failure())
        .singleElement()
        .satisfies(note -> assertThat(note.fix()).contains("narrativetrace-doctor"));
  }

  @Test
  void everyStepCarriesItsOwnVerify() {
    assertThat(skill.steps()).allMatch(step -> step.verify() != null && !step.verify().isBlank());
  }

  private SkillStep step(String title) {
    return skill.steps().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
  }
}
