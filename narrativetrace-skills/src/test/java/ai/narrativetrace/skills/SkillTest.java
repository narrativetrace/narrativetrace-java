/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillTest {

  private static SkillStep step(String title, List<String> commands, String verify) {
    return new SkillStep(title, new StepBody.CommandStep(commands), verify);
  }

  private Skill minimal(List<SkillStep> steps) {
    return new Skill(
        "narrativetrace-example",
        SkillClass.MECHANICAL,
        "A description.",
        null,
        "sixty-seconds",
        steps,
        List.of(),
        List.of(),
        CommandVocabulary.JAVA);
  }

  @Test
  void rejectsBlankOrNullRequiredFields() {
    var steps = List.of(step("t", List.of(), "git status"));
    assertThatThrownBy(
            () ->
                new Skill(
                    " ",
                    SkillClass.MECHANICAL,
                    "d",
                    null,
                    "fx",
                    steps,
                    List.of(),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Skill("name", null, "d", null, "fx", steps, List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Skill(
                    "name",
                    SkillClass.MECHANICAL,
                    " ",
                    null,
                    "fx",
                    steps,
                    List.of(),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Skill(
                    "name",
                    SkillClass.MECHANICAL,
                    "d",
                    null,
                    " ",
                    steps,
                    List.of(),
                    List.of(),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnEmptyStepList() {
    assertThatThrownBy(() -> minimal(List.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void whenToUseOptionalReflectsNullness() {
    assertThat(minimal(List.of(step("t", List.of(), null))).whenToUseOptional()).isEmpty();
  }

  @Test
  void descriptionFitsBudgetIsFalseWhenOverLength() {
    String longDescription = "x".repeat(Skill.DESCRIPTION_BUDGET_CHARS + 1);
    Skill skill =
        new Skill(
            "name",
            SkillClass.MECHANICAL,
            longDescription,
            null,
            "fx",
            List.of(step("t", List.of(), null)),
            List.of(),
            List.of(),
            List.of());
    assertThat(skill.descriptionFitsBudget()).isFalse();
  }

  @Test
  void commandStringsCollectsBodyCommandsAndReplayableVerifies() {
    Skill skill =
        minimal(
            List.of(
                step("a", List.of("./gradlew :sixty-seconds:build"), "git status"),
                step("b", List.of(), "the report is well-formed")));

    assertThat(skill.commandStrings())
        .containsExactly("./gradlew :sixty-seconds:build", "git status");
  }

  @Test
  void vocabularyViolationsNamesCommandsOutsideTheVocabulary() {
    Skill skill = minimal(List.of(step("a", List.of("npm install"), null)));
    assertThat(skill.vocabularyViolations()).containsExactly("npm install");
  }

  @Test
  void vocabularyViolationsIsEmptyWhenClean() {
    Skill skill = minimal(List.of(step("a", List.of("./gradlew build"), null)));
    assertThat(skill.vocabularyViolations()).isEmpty();
  }

  @Test
  void stepsWithoutVerifyOrFlagNamesTheOffendingSteps() {
    Skill skill = minimal(List.of(step("no-verify-no-flag", List.of(), null)));
    assertThat(skill.stepsWithoutVerifyOrFlag())
        .extracting(SkillStep::title)
        .containsExactly("no-verify-no-flag");
  }
}
