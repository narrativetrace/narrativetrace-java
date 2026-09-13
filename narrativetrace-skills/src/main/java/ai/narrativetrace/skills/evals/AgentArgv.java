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

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the agent CLI's argv list from a {@code --agent-command} template and a prompt, without
 * ever routing the prompt through a shell.
 *
 * <p>INTENT: the runner used to splice the prompt into a command string executed via {@code sh -c}
 * — a backtick or {@code $(...)} inside the prompt (a check id copied from a grader, say) ran as a
 * shell command substitution before the agent ever saw it. Tokenizing the static parts of the
 * template (never the prompt) and substituting the {@code {prompt}} placeholder as one opaque argv
 * element closes that: the prompt reaches {@code ProcessBuilder} as a single array element, which
 * the OS hands to the child process verbatim — no parser ever looks inside it again.
 *
 * @llmNote the tokenizer only understands the whitespace/quoting shape of the fixed platform
 *     presets ({@link Platform#presetAgentCommand}) or an operator-supplied {@code --agent-command}
 *     override — it is not a general shell grammar, and it must never be handed untrusted text to
 *     tokenize. The prompt itself is never tokenized: it is substituted after tokenizing, as one
 *     whole value.
 */
public final class AgentArgv {

  private AgentArgv() {}

  /**
   * Tokenizes {@code template} (double- or single-quoted segments respected, exactly as the
   * platform presets write {@code "{prompt}"}) and substitutes any token that is exactly {@code
   * {prompt}} with {@code prompt}, verbatim, as one argv element.
   *
   * @param template the {@code {prompt}}-templated command, e.g. {@code claude -p "{prompt}"
   *     --model haiku}
   * @param prompt the literal prompt text, however it is shaped — backticks, {@code $(...)}, quotes
   *     and newlines included
   * @return the argv list to hand a {@code ProcessBuilder}, never through a shell
   */
  public static List<String> build(String template, String prompt) {
    if (template == null || template.isBlank()) {
      throw new IllegalArgumentException("template must not be blank");
    }
    if (prompt == null) {
      throw new IllegalArgumentException("prompt must not be null");
    }
    List<String> argv = new ArrayList<>();
    for (String token : new Tokenizer(template).tokenize()) {
      argv.add("{prompt}".equals(token) ? prompt : token);
    }
    return List.copyOf(argv);
  }

  /**
   * A single-use scanner over one template string, discarded after {@link #tokenize()} returns —
   * never a long-lived field, so its mutable {@link StringBuilder} carries none of {@code
   * AvoidStringBufferField}'s leak concern.
   */
  @SuppressWarnings("PMD.AvoidStringBufferField") // NOPMD: scoped to one build() call, then GC'd
  private static final class Tokenizer {
    private final String template;
    private final List<String> tokens = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private boolean inToken;
    private int i;

    Tokenizer(String template) {
      this.template = template;
    }

    List<String> tokenize() {
      while (i < template.length()) {
        char c = template.charAt(i);
        if (Character.isWhitespace(c)) {
          closeToken();
          i++;
        } else if (c == '"' || c == '\'') {
          consumeQuoted(c);
        } else {
          current.append(c);
          inToken = true;
          i++;
        }
      }
      closeToken();
      return tokens;
    }

    private void closeToken() {
      if (inToken) {
        tokens.add(current.toString());
        current.setLength(0);
        inToken = false;
      }
    }

    private void consumeQuoted(char quote) {
      i++;
      while (i < template.length() && template.charAt(i) != quote) {
        current.append(template.charAt(i));
        i++;
      }
      if (i >= template.length()) {
        throw new IllegalArgumentException("unterminated " + quote + " in template: " + template);
      }
      i++;
      inToken = true;
    }
  }
}
