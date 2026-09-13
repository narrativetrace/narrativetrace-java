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

import java.util.List;
import java.util.Optional;

/**
 * One step of a skill. {@code verify} is the step's own definition of done, in prose — the
 * redaction step's verify names the {@code trap.redaction-proof} check, not a finding count (frozen
 * ruling, {@code agent-skills-2026-09-12.md} §7 ruling 9). The actual replayable action lives in
 * {@link #body()}: a {@link StepBody.CommandStep}'s commands are what {@code SkillReplayer}
 * executes and requires a clean exit from; {@code verify}'s prose is what a person or agent reads
 * to know what "done" means, including the parts a shell exit code cannot itself express (e.g. "the
 * report names this check", not just "the command exited 0").
 *
 * <p>A step with an empty {@link StepBody.CommandStep} and no {@code verify} is judgmental — its
 * title is expected to appear in {@code Lints#JUDGMENTAL_STEP_TITLES}. {@code flag}, when present,
 * is shown verbatim on the rendered page (e.g. {@code "unstudied — eval cell pending"}) — pointers
 * are product surface and must be honest.
 */
public record SkillStep(
    String title, StepBody body, String verify, List<FailureNote> failure, String flag) {

  public SkillStep {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("a SkillStep's title must not be blank");
    }
    if (body == null) {
      throw new IllegalArgumentException("a SkillStep's body must not be null");
    }
    failure = List.copyOf(failure);
  }

  /** Convenience constructor for the common case: no failure notes, no flag. */
  public SkillStep(String title, StepBody body, String verify) {
    this(title, body, verify, List.of(), null);
  }

  public Optional<String> verifyOptional() {
    return Optional.ofNullable(verify);
  }

  public Optional<String> flagOptional() {
    return Optional.ofNullable(flag);
  }

  /**
   * True when {@link #verify} is itself a replayable command (first token in the vocabulary), as
   * opposed to prose describing done-ness that a shell exit code cannot express on its own.
   */
  public boolean hasReplayableVerify() {
    return verify != null && CommandVocabulary.JAVA.contains(CommandVocabulary.firstToken(verify));
  }
}
