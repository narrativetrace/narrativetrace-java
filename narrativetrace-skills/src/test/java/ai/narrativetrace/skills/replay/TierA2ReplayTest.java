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
package ai.narrativetrace.skills.replay;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tier A2: every mechanical/guided step of every catalogue skill actually executes against the
 * sixty-seconds fixture, right now, in this commit. No LLM, deterministic — a judgmental step (no
 * commands, no mechanically-checkable verify) is expected to report {@code ran=false, ok=true}
 * rather than being skipped outright, so a catalogue change that accidentally adds commands to a
 * step nobody meant to make mechanical is still visible in the replay's own step list.
 */
class TierA2ReplayTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  static List<Skill> skills() {
    return CatalogueIndex.ALL;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("skills")
  void everyStepReplaysCleanlyAgainstTheFixture(Skill skill) {
    List<StepReplay> results = SkillReplayer.replay(skill, REPO_ROOT);

    assertThat(results).hasSameSizeAs(skill.steps());
    assertThat(results)
        .as("every replayed step must exit clean: " + results)
        .allMatch(StepReplay::ok);
    assertThat(results)
        .as("at least one step must actually run — a skill that replays nothing proves nothing")
        .anyMatch(StepReplay::ran);
  }
}
