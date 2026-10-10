/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import ai.narrativetrace.skills.evals.RepoRoot;
import ai.narrativetrace.skills.lint.Lints;
import ai.narrativetrace.skills.render.ClaudeSkillRenderer;
import ai.narrativetrace.skills.render.CodexSkillRenderer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Adversarial probes for the debug skill, the shared pin and the debug command constants: step
 * ordering against each step's own verify, condition wording, the lint pass, rendering parity, the
 * shared never-list, immutability and shell hygiene of the command strings.
 */
class AdversarialPhase7M2Test {

  private final Skill debug = NarrativeTraceDebugSkill.build();
  private final Skill verify = NarrativeTraceVerifySkill.build();

  private static final String ASK = "Ask once whether to pin it, then stop the turn";
  private static final String PROMOTE = "Promote what was shown, and nothing else";

  private int indexOfStep(String title) {
    List<String> titles = debug.steps().stream().map(SkillStep::title).toList();
    int at = titles.indexOf(title);
    assertThat(at).as("step \"%s\" exists in the debug skill", title).isGreaterThanOrEqualTo(0);
    return at;
  }

  // --- step ordering against each step's own verify ---

  @Test
  void everyConditionalStepSaysWhatToDoWhenTheConditionDoesNotHold() {
    List<SkillStep> conditional =
        debug.steps().stream().filter(s -> s.conditionOptional().isPresent()).toList();

    assertThat(conditional).isNotEmpty();
    for (SkillStep step : conditional) {
      assertThat(step.condition())
          .as("condition of \"%s\" says what to do otherwise", step.title())
          .containsAnyOf("otherwise", "go straight", "go on to");
    }
  }

  @Test
  void theAskStepStopsTheTurnBeforeAnyPromotionStep() {
    assertThat(indexOfStep(ASK)).isLessThan(indexOfStep(PROMOTE));
    assertThat(debug.steps().get(indexOfStep(ASK)).verify())
        .contains("the reply ends with the question");
  }

  @Test
  void noStepBeforeTheAskRunsTheApprovalCommand() {
    int ask = indexOfStep(ASK);
    List<String> before =
        debug.steps().subList(0, ask).stream()
            .flatMap(
                s ->
                    s.body() instanceof StepBody.CommandStep c
                        ? c.commands().stream()
                        : Stream.<String>empty())
            .toList();

    assertThat(before).doesNotContain(VerifyCommands.APPROVE);
  }

  // --- the closed vocabulary and the lints ---

  @Test
  void theDebugSkillPassesEveryLintCheckWhenGivenAlone() {
    List<Skill> skills = List.of(debug);

    assertThat(Lints.vocabularyViolations(skills)).isEmpty();
    assertThat(Lints.allowedToolsViolations(skills)).isEmpty();
    assertThat(Lints.promotionNotPreApproved(skills)).isEmpty();
    assertThat(Lints.citationViolations(skills)).isEmpty();
    assertThat(Lints.stepsWithoutVerifyOrFlag(skills)).isEmpty();
    assertThat(Lints.descriptionBudgetViolations(skills)).isEmpty();
  }

  @Test
  void everyCommandTheDebugSkillInvokesIsInTheClosedVocabulary() {
    assertThat(debug.commandStrings()).isNotEmpty();
    for (String command : debug.commandStrings()) {
      assertThat(CommandVocabulary.JAVA)
          .as("first token of \"%s\"", command)
          .contains(CommandVocabulary.firstToken(command));
    }
  }

  // --- rendering: the Claude page and its Codex sibling ---

  @Test
  void theRenderedClaudePageHasEveryStepTitleInOrderNumberedFromOne() {
    String page = ClaudeSkillRenderer.render(debug, RepoRoot.locate());

    int at = 0;
    int number = 1;
    for (SkillStep step : debug.steps()) {
      String heading = "## " + number + ". " + step.title() + "\n";
      int found = page.indexOf(heading, at);
      assertThat(found)
          .as("heading \"%s\" appears after the previous step", heading)
          .isGreaterThanOrEqualTo(0);
      at = found + heading.length();
      number++;
    }
  }

  @Test
  void theRenderedClaudePageCarriesBothTraceReadingSectionsOnceInTheRightPlace() {
    String page = ClaudeSkillRenderer.render(debug, RepoRoot.locate());
    String flavours = "## " + TraceReading.FLAVOURS.heading() + "\n";
    String shapes = "## " + TraceReading.SHAPES.heading() + "\n";

    assertThat(page).containsOnlyOnce(flavours).containsOnlyOnce(shapes);
    SkillStep last = debug.steps().get(debug.steps().size() - 1);
    assertThat(page.indexOf("## " + debug.steps().size() + ". " + last.title() + "\n"))
        .isLessThan(page.indexOf(flavours));
    assertThat(page.indexOf(flavours)).isLessThan(page.indexOf(shapes));
    assertThat(page.indexOf(shapes)).isLessThan(page.indexOf("## Always\n"));
    assertThat(page.indexOf("## Always\n")).isLessThan(page.indexOf("## Never\n"));
  }

  @Test
  void theRenderedClaudePageDeclaresNoPreApprovedTool() {
    String page = ClaudeSkillRenderer.render(debug, RepoRoot.locate());

    assertThat(page).doesNotContain("allowed-tools:").doesNotContain("Bash(");
  }

  @Test
  void everyLineMentioningNotTracedInTheRenderedPageIsTheProhibition() {
    String page = ClaudeSkillRenderer.render(debug, RepoRoot.locate());
    List<String> mentions = page.lines().filter(line -> line.contains("@NotTraced")).toList();

    assertThat(mentions).isNotEmpty();
    assertThat(mentions).allMatch(line -> line.contains("never @NotTraced"));
  }

  @Test
  void theCodexPageCarriesTheSameBodyAsTheClaudePageAfterItsFrontmatter() {
    Path root = RepoRoot.locate();
    String claude = ClaudeSkillRenderer.render(debug, root);
    String codex = CodexSkillRenderer.render(debug, root);
    String heading = "# narrativetrace-debug\n";

    assertThat(codex.substring(codex.indexOf(heading)))
        .isEqualTo(claude.substring(claude.indexOf(heading)));
  }

  // --- the shared pin, the never-list and the rule lists ---

  @Test
  void theVerifyAndDebugSkillsShareTheBaselinePinNeverAsAContiguousSublist() {
    assertThat(Collections.indexOfSubList(debug.never(), BaselinePin.NEVER))
        .isGreaterThanOrEqualTo(0);
    assertThat(Collections.indexOfSubList(verify.never(), BaselinePin.NEVER))
        .isGreaterThanOrEqualTo(0);
  }

  @Test
  void noRuleAppearsTwiceInEitherSkillsAlwaysOrNeverList() {
    for (Skill skill : List.of(debug, verify)) {
      assertThat(skill.always().stream().map(ReasonedRule::rule).toList())
          .as("always rules of %s", skill.canonicalName())
          .doesNotHaveDuplicates();
      assertThat(skill.never().stream().map(ReasonedRule::rule).toList())
          .as("never rules of %s", skill.canonicalName())
          .doesNotHaveDuplicates();
    }
  }

  @Test
  void theBaselinePinStepsListCannotBeGrownSetOrOverwritten() {
    List<SkillStep> pin = BaselinePin.steps();

    assertThatThrownBy(() -> pin.add(pin.get(0))).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> pin.set(0, pin.get(1)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // --- command constants: shell hygiene ---

  @Test
  void theDebugSkillsDiscoveryCommandsCarryNoShellMetacharacters() {
    List<String> commands =
        List.of(
            DebugCommands.FIND_DIAGRAMS,
            VerifyCommands.FIND_STRUCTURAL,
            VerifyCommands.FIND_NARRATIVES,
            VerifyCommands.FIND_RECEIVED,
            VerifyCommands.RUN_THE_SUITE,
            VerifyCommands.APPROVE);

    for (String command : commands) {
      assertThat(command)
          .as("\"%s\" is a single plain command", command)
          .doesNotContain("|")
          .doesNotContain(";")
          .doesNotContain("&")
          .doesNotContain("`")
          .doesNotContain("$(");
    }
  }
}
