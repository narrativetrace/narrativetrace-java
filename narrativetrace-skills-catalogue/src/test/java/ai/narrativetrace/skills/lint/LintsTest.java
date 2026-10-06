/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.lint;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.CommandVocabulary;
import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.ProListing;
import ai.narrativetrace.skills.ProListingStatus;
import ai.narrativetrace.skills.ReasonedRule;
import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillClass;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LintsTest {

  private static Skill skillWithDescription(String description) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        description,
        null,
        "sixty-seconds",
        List.of(new SkillStep("Step", new StepBody.CommandStep(List.of()), "verify")),
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  @Test
  void descriptionBudgetViolationsNamesAnOverBudgetSkill() {
    Skill skill = skillWithDescription("x".repeat(Skill.DESCRIPTION_BUDGET_CHARS + 1));
    assertThat(Lints.descriptionBudgetViolations(List.of(skill))).isNotEmpty();
  }

  @Test
  void descriptionBudgetViolationsFlagsTheCatalogueWideTotalToo() {
    // Enough skills, each exactly at (never over) the per-skill budget, that the sum crosses the
    // much larger catalogue-wide one — no single skill trips its own per-skill violation.
    String atBudget = "x".repeat(Skill.DESCRIPTION_BUDGET_CHARS);
    int countNeededToExceedCatalogueBudget =
        (Lints.CATALOGUE_CHAR_BUDGET / Skill.DESCRIPTION_BUDGET_CHARS) + 1;
    List<Skill> skills =
        java.util.stream.IntStream.range(0, countNeededToExceedCatalogueBudget)
            .mapToObj(i -> skillWithDescription(atBudget))
            .toList();

    assertThat(Lints.descriptionBudgetViolations(skills))
        .as("no single skill is over its own budget, only the catalogue-wide sum")
        .anyMatch(v -> v.contains("catalogue-wide"))
        .noneMatch(v -> v.contains("narrativetrace-x: description is"));
  }

  @Test
  void vocabularyViolationsNamesTheSkillAndCommand() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "d",
            null,
            "sixty-seconds",
            List.of(new SkillStep("s", new StepBody.CommandStep(List.of("npm install")), null)),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);
    assertThat(Lints.vocabularyViolations(List.of(skill)))
        .containsExactly("narrativetrace-x: \"npm install\" is outside the vocabulary");
  }

  private static Skill skillAllowing(List<String> allowedTools) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.MECHANICAL,
        "d",
        null,
        "sixty-seconds",
        List.of(new SkillStep("s", new StepBody.CommandStep(List.of("git status")), null)),
        List.of(),
        List.of(),
        allowedTools);
  }

  @Test
  void allowedToolsViolationsRejectsAPreSpelledClaudeToolPattern() {
    assertThat(Lints.allowedToolsViolations(List.of(skillAllowing(List.of("Bash(git *)")))))
        .containsExactly(
            "narrativetrace-x: allowed tool \"Bash(git *)\" is a rendered platform spelling — "
                + "declare the bare command and let the renderer spell it");
  }

  @Test
  void allowedToolsViolationsRejectsACommandOutsideTheClosedVocabulary() {
    assertThat(Lints.allowedToolsViolations(List.of(skillAllowing(List.of("npm")))))
        .containsExactly("narrativetrace-x: allowed tool \"npm\" is outside the vocabulary");
  }

  @Test
  void stepsWithoutVerifyOrFlagAllowsAKnownJudgmentalTitle() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "d",
            null,
            "sixty-seconds",
            List.of(
                new SkillStep(
                    "Read the rendered trace before asserting",
                    new StepBody.CommandStep(List.of()),
                    null)),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);
    assertThat(Lints.stepsWithoutVerifyOrFlag(List.of(skill))).isEmpty();
  }

  @Test
  void stepsWithoutVerifyOrFlagRejectsAnUnknownJudgmentalTitle() {
    Skill offending =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "d",
            null,
            "sixty-seconds",
            List.of(new SkillStep("Some new step", new StepBody.CommandStep(List.of()), null)),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);
    assertThat(Lints.stepsWithoutVerifyOrFlag(List.of(offending))).isNotEmpty();
  }

  @Test
  void missingFixturesNamesASkillWhoseFixtureDoesNotExist(@TempDir Path repoRoot) {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "d",
            null,
            "no-such-fixture",
            List.of(new SkillStep("s", new StepBody.CommandStep(List.of()), "v")),
            List.of(),
            List.of(),
            CommandVocabulary.JAVA);
    assertThat(Lints.missingFixtures(List.of(skill), repoRoot)).isNotEmpty();
  }

  @Test
  void missingFixturesIsEmptyWhenTheFixtureExists(@TempDir Path repoRoot) throws Exception {
    java.nio.file.Files.createDirectories(repoRoot.resolve("sixty-seconds"));
    Skill skill = skillWithDescription("d");
    assertThat(Lints.missingFixtures(List.of(skill), repoRoot)).isEmpty();
  }

  @Test
  void duplicateCanonicalNamesFindsTheDuplicate() {
    Skill a = skillWithDescription("a");
    Skill b = skillWithDescription("b");
    assertThat(Lints.duplicateCanonicalNames(List.of(a, b))).isNotEmpty();
  }

  @Test
  void proListingStatusDisagreementsCoversBothViolationShapes() {
    ProListing listing =
        new ProListing(
            "narrativetrace-pro-x",
            "prompt",
            "delivers",
            "needs",
            "comes from",
            ProListingStatus.SHIPPED,
            "shipped");

    assertThat(Lints.proListingStatusDisagreements(List.of(listing), Map.of())).isNotEmpty();
    assertThat(
            Lints.proListingStatusDisagreements(
                List.of(listing), Map.of("narrativetrace-pro-x", "in development")))
        .isNotEmpty();
    assertThat(
            Lints.proListingStatusDisagreements(
                List.of(listing), Map.of("narrativetrace-pro-x", "shipped")))
        .isEmpty();
  }

  @Test
  void citationViolationsCatchesEachForbiddenShape() {
    assertThat(citationHits("see planning/skill-design.md for the rationale")).isNotEmpty();
    assertThat(citationHits("do not port narrative-trace-java-pro details here")).isNotEmpty();
    assertThat(citationHits("configured in .gitlab-ci.yml")).isNotEmpty();
    assertThat(citationHits("per §7 ruling 9")).isNotEmpty();
    assertThat(citationHits("commit deadbeefc landed this")).isNotEmpty();
  }

  @Test
  void citationViolationsScansEveryProseFieldOfAStep() {
    Skill skill =
        new Skill(
            "narrativetrace-x",
            SkillClass.MECHANICAL,
            "clean description",
            "clean when-to-use",
            "sixty-seconds",
            List.of(
                new SkillStep(
                    "clean title",
                    new StepBody.CodeStep("java", "planning/leak in code"),
                    "clean verify",
                    List.of(new FailureNote("s", "c", "planning/leak in fix")),
                    "planning/leak in flag")),
            List.of(new ReasonedRule("planning/leak in rule", "reason")),
            List.of(new ReasonedRule("rule", "planning/leak in reason")),
            CommandVocabulary.JAVA);
    assertThat(Lints.citationViolations(List.of(skill))).hasSizeGreaterThanOrEqualTo(4);
  }

  @Test
  void citationViolationsIsCleanForOrdinaryText() {
    assertThat(citationHits("run the tutorial and check the output")).isEmpty();
  }

  private static List<String> citationHits(String prose) {
    return Lints.citationViolations(List.of(skillWithDescription(prose)));
  }

  // --- publishingNotPreApproved ------------------------------------------------------------------

  private static Skill reportingSkill(List<String> allowedTools) {
    return new Skill(
        "narrativetrace-x",
        SkillClass.GUIDED,
        "d",
        null,
        "sixty-seconds",
        List.of(
            new SkillStep(
                "report it",
                new StepBody.CommandStep(
                    List.of("./gradlew narrativetraceFeedback --channel url --category library")),
                null)),
        List.of(),
        List.of(),
        allowedTools);
  }

  @Test
  void publishingNotPreApprovedRejectsASkillThatPreApprovesItsOwnReportingCommand() {
    assertThat(Lints.publishingNotPreApproved(List.of(reportingSkill(List.of("./gradlew")))))
        .singleElement()
        .asString()
        .contains("pre-approves its own publishing command")
        .contains("let the harness ask");
  }

  @Test
  void publishingNotPreApprovedAcceptsTheSameSkillDeclaringNoAllowedTool() {
    assertThat(Lints.publishingNotPreApproved(List.of(reportingSkill(List.of())))).isEmpty();
  }

  @Test
  void publishingNotPreApprovedIgnoresASkillWhoseCommandsPublishNothing() {
    assertThat(Lints.publishingNotPreApproved(List.of(skillAllowing(List.of("git", "./gradlew")))))
        .as("pre-approving an ordinary command is the whole point of the field")
        .isEmpty();
  }
}
