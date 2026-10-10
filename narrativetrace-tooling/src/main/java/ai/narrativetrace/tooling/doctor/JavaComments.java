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
package ai.narrativetrace.tooling.doctor;

/**
 * Java source text with its comments removed, so a wiring check reads what the program does and not
 * what a comment says about it.
 *
 * <p>INTENT: {@code // @EnableNarrativeTrace(...)} or a Javadoc that explains the wiring is not the
 * wiring. A string or character literal is copied verbatim — a {@code //} inside {@code "http://x"}
 * starts nothing — and a block comment becomes one space, so the tokens either side of it never
 * fuse. A line comment keeps its line break.
 *
 * <p><b>@llmNote</b> A scanner, not a regular expression: it walks the text once, so there is no
 * backtracking on text a project supplies. It is for Java sources only — a properties file's {@code
 * /*} is a value, and an unterminated block comment here would swallow the rest of the text.
 */
final class JavaComments {

  private JavaComments() {}

  /** {@code source} without its line and block comments. */
  static String strip(String source) {
    StringBuilder out = new StringBuilder(source.length());
    int i = 0;
    while (i < source.length()) {
      int next = endOfSkipped(source, i);
      if (next > i) {
        out.append(source, i, next);
        i = next;
      } else {
        i = skipComment(source, i, out);
      }
    }
    return out.toString();
  }

  /** The end of the string or character literal opening at {@code at}, or {@code at} if none. */
  private static int endOfSkipped(String s, int at) {
    char c = s.charAt(at);
    if (s.startsWith("\"\"\"", at)) {
      int close = s.indexOf("\"\"\"", at + 3);
      return close < 0 ? s.length() : close + 3;
    }
    if (c != '"' && c != '\'') {
      return at + (isCommentStart(s, at) ? 0 : 1);
    }
    int i = at + 1;
    while (i < s.length() && s.charAt(i) != c && s.charAt(i) != '\n') {
      i += s.charAt(i) == '\\' ? 2 : 1;
    }
    return Math.min(i + 1, s.length());
  }

  private static boolean isCommentStart(String s, int at) {
    return s.startsWith("//", at) || s.startsWith("/*", at);
  }

  /** Skips the comment at {@code at}, appending what stands in for it; returns the next index. */
  private static int skipComment(String s, int at, StringBuilder out) {
    if (s.startsWith("//", at)) {
      int eol = s.indexOf('\n', at);
      return eol < 0 ? s.length() : eol;
    }
    int close = s.indexOf("*/", at + 2);
    out.append(' ');
    return close < 0 ? s.length() : close + 2;
  }
}
