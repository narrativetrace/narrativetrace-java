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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A typed skill: the single source of truth {@code SKILL.md} and the {@code AGENTS.md} managed
 * section are both rendered from, never hand-edited. Mirrors the TypeScript reference's {@code
 * packages/skills/src/skill.ts} shape, adapted to Java's closed command vocabulary and the free
 * repo's own conventions.
 */
public record Skill(
    String canonicalName,
    SkillClass skillClass,
    String description,
    String whenToUse,
    String fixture,
    List<SkillStep> steps,
    List<ReasonedRule> always,
    List<ReasonedRule> never,
    List<String> allowedTools) {

  /** Catalogue-wide description budget is a Tier A lint; this is the per-skill share of it. */
  public static final int DESCRIPTION_BUDGET_CHARS = 1024;

  public Skill {
    if (canonicalName == null || canonicalName.isBlank()) {
      throw new IllegalArgumentException("a Skill's canonicalName must not be blank");
    }
    if (skillClass == null) {
      throw new IllegalArgumentException("a Skill's skillClass must not be null");
    }
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("a Skill's description must not be blank");
    }
    if (fixture == null || fixture.isBlank()) {
      throw new IllegalArgumentException("a Skill's fixture must not be blank");
    }
    steps = List.copyOf(steps);
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("a Skill must declare at least one step");
    }
    always = List.copyOf(always);
    never = List.copyOf(never);
    allowedTools = List.copyOf(allowedTools);
  }

  public Optional<String> whenToUseOptional() {
    return Optional.ofNullable(whenToUse);
  }

  public boolean descriptionFitsBudget() {
    return description.length() <= DESCRIPTION_BUDGET_CHARS;
  }

  /**
   * Every command string this skill's steps invoke: step bodies and replayable verifies alike (a
   * verify that is prose rather than a command is excluded — see {@link SkillStep}).
   */
  public List<String> commandStrings() {
    List<String> commands = new ArrayList<>();
    for (SkillStep step : steps) {
      if (step.body() instanceof StepBody.CommandStep commandStep) {
        commands.addAll(commandStep.commands());
      }
      if (step.hasReplayableVerify()) {
        commands.add(step.verify());
      }
    }
    return List.copyOf(commands);
  }

  /** Commands whose first token falls outside the closed vocabulary — empty means clean. */
  public List<String> vocabularyViolations() {
    return commandStrings().stream()
        .filter(command -> !CommandVocabulary.JAVA.contains(CommandVocabulary.firstToken(command)))
        .toList();
  }

  /** Steps carrying neither a verify nor a flag — every such step must be judgmental by title. */
  public List<SkillStep> stepsWithoutVerifyOrFlag() {
    return steps.stream().filter(s -> s.verify() == null && s.flag() == null).toList();
  }
}
