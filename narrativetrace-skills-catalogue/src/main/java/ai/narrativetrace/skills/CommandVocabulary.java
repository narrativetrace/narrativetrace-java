/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
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
 * <p>A {@link List}, not a {@code Set}: anything rendered from it (the plugin README's list of what
 * the skills run) must be byte-stable across renders, and {@code Set.of}'s iteration order is
 * unspecified.
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
