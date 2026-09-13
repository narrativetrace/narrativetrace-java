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

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformTest {

  @Test
  void parseIsCaseInsensitiveForEveryKnownPlatform() {
    assertThat(Platform.parse("claude")).contains(Platform.CLAUDE);
    assertThat(Platform.parse("CODEX")).contains(Platform.CODEX);
    assertThat(Platform.parse("Gemini")).contains(Platform.GEMINI);
  }

  @Test
  void parseIsEmptyForUnknownOrNullInput() {
    assertThat(Platform.parse("chatgpt")).isEmpty();
    assertThat(Platform.parse(null)).isEqualTo(Optional.empty());
  }

  @Test
  void onlyCodexAndGeminiAreSporadic() {
    assertThat(Platform.CLAUDE.isSporadic()).isFalse();
    assertThat(Platform.CODEX.isSporadic()).isTrue();
    assertThat(Platform.GEMINI.isSporadic()).isTrue();
  }

  @Test
  void onlyTheDoctorSkillIsReadOnly() {
    assertThat(Platform.isReadOnlySkill("narrativetrace-doctor")).isTrue();
    assertThat(Platform.isReadOnlySkill("add-narrative-tracing")).isFalse();
  }

  @Test
  void claudePresetNamesTheModelAndAllowsBash() {
    assertThat(Platform.CLAUDE.presetAgentCommand("haiku", "add-narrative-tracing"))
        .isEqualTo("claude -p \"{prompt}\" --model haiku --allowed-tools Bash");
  }

  @Test
  void codexPresetSandboxesReadOnlyForTheDoctorSkillOnly() {
    assertThat(Platform.CODEX.presetAgentCommand("mini", "narrativetrace-doctor"))
        .isEqualTo("codex exec --sandbox read-only --model mini \"{prompt}\"");
    assertThat(Platform.CODEX.presetAgentCommand("mini", "add-narrative-tracing"))
        .isEqualTo("codex exec --sandbox workspace-write --model mini \"{prompt}\"");
  }

  @Test
  void geminiPresetApprovalModeIsPlanForTheDoctorSkillOnly() {
    assertThat(Platform.GEMINI.presetAgentCommand("flash", "narrativetrace-doctor"))
        .isEqualTo("gemini -p \"{prompt}\" --model flash --approval-mode plan");
    assertThat(Platform.GEMINI.presetAgentCommand("flash", "add-narrative-tracing"))
        .isEqualTo("gemini -p \"{prompt}\" --model flash --approval-mode auto_edit");
  }
}
