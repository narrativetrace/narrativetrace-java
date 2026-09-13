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
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EvalRunnerArgsTest {

  @Test
  void parsesTheFullFlagSet() {
    EvalRunnerArgs args =
        EvalRunnerArgs.parse(
            new String[] {
              "--skill", "narrativetrace-doctor",
              "--case", "happy-path",
              "--platform", "codex",
              "--model", "mini",
              "--agent-command", "codex exec \"{prompt}\"",
              "--trials", "3"
            });
    assertThat(args.skill()).isEqualTo("narrativetrace-doctor");
    assertThat(args.caseName()).isEqualTo("happy-path");
    assertThat(args.platform()).isEqualTo(Platform.CODEX);
    assertThat(args.model()).isEqualTo("mini");
    assertThat(args.agentCommandOverride()).contains("codex exec \"{prompt}\"");
    assertThat(args.trials()).isEqualTo(3);
  }

  @Test
  void defaultsTrialsToOneAndLeavesTheAgentCommandOverrideEmpty() {
    EvalRunnerArgs args =
        EvalRunnerArgs.parse(
            new String[] {
              "--skill", "s", "--case", "c", "--platform", "claude", "--model", "haiku"
            });
    assertThat(args.trials()).isEqualTo(1);
    assertThat(args.agentCommandOverride()).isEmpty();
  }

  @Test
  void rejectsAMissingRequiredFlag() {
    assertThatThrownBy(
            () ->
                EvalRunnerArgs.parse(
                    new String[] {"--skill", "s", "--case", "c", "--model", "haiku"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Usage:");
  }

  @Test
  void rejectsAFlagWithNoTrailingValue() {
    assertThatThrownBy(
            () -> EvalRunnerArgs.parse(new String[] {"--skill", "s", "--case", "c", "--platform"}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnUnknownPlatform() {
    assertThatThrownBy(
            () ->
                EvalRunnerArgs.parse(
                    new String[] {
                      "--skill", "s", "--case", "c", "--platform", "chatgpt", "--model", "m"
                    }))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructorRejectsEachBlankOrInvalidField() {
    assertThatThrownBy(() -> new EvalRunnerArgs(" ", "c", Platform.CLAUDE, "m", null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalRunnerArgs("s", " ", Platform.CLAUDE, "m", null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalRunnerArgs("s", "c", null, "m", null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalRunnerArgs("s", "c", Platform.CLAUDE, " ", null, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvalRunnerArgs("s", "c", Platform.CLAUDE, "m", null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
