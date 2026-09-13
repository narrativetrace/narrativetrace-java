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

/**
 * The closed per-port command vocabulary a Java skill's steps may invoke ({@code
 * skill-harness-design.md} principle 7): the repo's own Gradle wrapper — never a global {@code
 * gradle} — {@code git}, and {@code find} (read-only discovery of a rendered output file; no
 * pipeline, no shell). A skill may never instruct installing a global tool; anything else it needs
 * arrives through the wrapper into the project, where the vocabulary already reaches it.
 *
 * <p>A {@link List}, not a {@code Set}: rendered frontmatter (e.g. {@code allowed-tools}) must be
 * byte-stable across renders, and {@code Set.of}'s iteration order is unspecified.
 */
public final class CommandVocabulary {

  public static final List<String> JAVA = List.of("./gradlew", "git", "find");

  private CommandVocabulary() {}

  /** The first whitespace-separated token of a command string, or "" for a blank command. */
  public static String firstToken(String command) {
    if (command == null) {
      return "";
    }
    String trimmed = command.strip();
    int space = trimmed.indexOf(' ');
    return space < 0 ? trimmed : trimmed.substring(0, space);
  }
}
