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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentArgv#build} is the fix for the shell-splicing defect: the prompt must reach the
 * returned argv as one opaque element, whatever it contains, never re-tokenized.
 */
class AgentArgvTest {

  @Test
  void substitutesThePromptPlaceholderAsOneArgvElement() {
    List<String> argv =
        AgentArgv.build("claude -p \"{prompt}\" --model haiku --allowed-tools Bash", "hello");

    assertThat(argv)
        .containsExactly("claude", "-p", "hello", "--model", "haiku", "--allowed-tools", "Bash");
  }

  @Test
  void aPromptContainingBackticksDollarParensQuotesAndNewlinesArrivesVerbatim() {
    String hostile = "before `id` $(whoami) \"double\" 'single'\nsecond line\nthird`";

    List<String> argv = AgentArgv.build("codex exec --sandbox read-only \"{prompt}\"", hostile);

    assertThat(argv).containsExactly("codex", "exec", "--sandbox", "read-only", hostile);
  }

  @Test
  void supportsAnUnquotedPromptPlaceholder() {
    List<String> argv = AgentArgv.build("echo-agent {prompt} --done", "a $(b) c");

    assertThat(argv).containsExactly("echo-agent", "a $(b) c", "--done");
  }

  @Test
  void supportsASingleQuotedPromptPlaceholder() {
    List<String> argv = AgentArgv.build("gemini -p '{prompt}' --model flash", "it's `dangerous`");

    assertThat(argv).containsExactly("gemini", "-p", "it's `dangerous`", "--model", "flash");
  }

  @Test
  void aTemplateWithNoPromptPlaceholderLeavesEveryTokenUnchanged() {
    List<String> argv = AgentArgv.build("true", "ignored");

    assertThat(argv).containsExactly("true");
  }

  @Test
  void rejectsABlankOrNullTemplate() {
    assertThatThrownBy(() -> AgentArgv.build(null, "p"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AgentArgv.build(" ", "p"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANullPrompt() {
    assertThatThrownBy(() -> AgentArgv.build("echo {prompt}", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnUnterminatedQuoteInTheTemplate() {
    assertThatThrownBy(() -> AgentArgv.build("claude -p \"{prompt}", "hi"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unterminated");
  }

  @Test
  void anEmptyPromptSubstitutesAsAnEmptyArgvElement() {
    List<String> argv = AgentArgv.build("gemini -p \"{prompt}\"", "");

    assertThat(argv).containsExactly("gemini", "-p", "");
  }
}
