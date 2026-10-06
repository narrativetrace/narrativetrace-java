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

import java.util.regex.Pattern;

/**
 * Rewrites an absolute home directory to {@code ~} — the one normalisation a draft performs before
 * the gate reads anything.
 *
 * <p>INTENT: a home path names the account it belongs to, and a problem report is full of them
 * because that is where people's projects live. Refusing the report would be the wrong answer to a
 * mistake nobody made on purpose; {@code ~} says the same thing about the same file and names
 * nobody. So this runs FIRST, and {@link ValueFreeRule#HOME_PATH} is left as the backstop for a
 * form this cannot normalise — a UNC share, a path typed into the body file after the draft was
 * shown.
 *
 * <p><b>@llmNote</b> {@code /home} with nothing after it is NOT a home directory, and neither is
 * {@code /usr/share/ada}: the account segment has to be present and has to follow one of the three
 * platform roots. A rewrite that was eager here would turn an ordinary path into {@code ~} and make
 * the report wrong instead of safe.
 */
public final class HomePaths {

  /**
   * The three platform roots followed by one account segment. Capturing group 1 is whatever follows
   * the account, so a trailing separator and everything after it survives the rewrite.
   */
  private static final Pattern HOME =
      Pattern.compile(
          "(?:/Users/|/home/)[^/\\s]+|[A-Za-z]:\\\\Users\\\\[^\\\\\\s]+", Pattern.CASE_INSENSITIVE);

  private HomePaths() {}

  /**
   * The text with every home directory replaced by {@code ~}.
   *
   * @throws IllegalArgumentException when {@code text} is null — an absent field is {@code ""}
   */
  public static String toTilde(String text) {
    if (text == null) {
      throw new IllegalArgumentException("the rewriter reads text, never null");
    }
    return HOME.matcher(text).replaceAll("~");
  }
}
