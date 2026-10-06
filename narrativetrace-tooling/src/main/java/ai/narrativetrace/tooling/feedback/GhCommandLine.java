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
package ai.narrativetrace.tooling.feedback;

/**
 * The exact {@code gh issue create} line that files this report — PRINTED, never run.
 *
 * <p>INTENT: the second yes-path, offered only when {@code gh} is present AND authenticated. It
 * saves the browser step for people who already live in that tool, and it is a convenience rather
 * than the foundation: {@code gh} ships on hosted runners and is absent from most ordinary
 * machines, which is why the pre-filled URL is the default and this is not.
 *
 * <p><b>@llmNote</b> This library never executes the line. Running it is the user's act, in their
 * own shell, with their own credential — and because {@code gh} is outside the skills' closed
 * command vocabulary, an agent that runs it is asked for permission by its harness as well. Two
 * independent gates, neither of which this code can bypass by printing something.
 *
 * <p><b>@llmNote</b> The title is single-quoted with {@code '\\''} escaping, and that is a security
 * property, not formatting. The step comes from a project — a test name, a check id, whatever the
 * agent read — and an unquoted title containing {@code ;} is a second command in a line we told
 * somebody to paste into their shell.
 */
public final class GhCommandLine {

  private GhCommandLine() {}

  /**
   * The one-line invocation.
   *
   * @param bodyFile where the body was written, as the user will type it
   * @throws IllegalArgumentException when the report is not a Java report, or the path is blank
   */
  public static String of(FeedbackReport report, String bodyFile) {
    PublicRepository.requireJavaRuntime(report);
    if (bodyFile == null || bodyFile.isBlank()) {
      throw new IllegalArgumentException(
          "the gh line files the body from a FILE — there is no line without one");
    }
    StringBuilder line = new StringBuilder("gh issue create");
    line.append(" --repo ").append(PublicRepository.SLUG);
    line.append(" --template ").append(PublicRepository.FORM);
    line.append(" --title ").append(singleQuote(oneLine(PublicRepository.titleFor(report))));
    line.append(" --body-file ").append(bodyFile.strip());
    for (String label : PublicRepository.labelsFor(report)) {
      line.append(" --label ").append(oneLine(label));
    }
    return line.toString();
  }

  /** A printed command is one line: a newline inside it would be a second command. */
  private static String oneLine(String value) {
    return value.replaceAll("\\s+", " ").strip();
  }

  /** POSIX single-quoting: the only quoting under which a shell interprets nothing. */
  private static String singleQuote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }
}
