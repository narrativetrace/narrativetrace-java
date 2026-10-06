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
 * <p>A {@link List}, not a {@code Set}: rendered frontmatter (e.g. {@code allowed-tools}) must be
 * byte-stable across renders, and {@code Set.of}'s iteration order is unspecified.
 */
public final class CommandVocabulary {

  public static final List<String> JAVA = List.of("./gradlew", "git", "find");

  private CommandVocabulary() {}

  /**
   * The Claude-flavour {@code allowed-tools} spelling of one vocabulary command: the {@code
   * Bash(<command> *)} tool pattern, e.g. {@code git} to {@code Bash(git *)}.
   *
   * <p>Verified against Claude Code's documented syntax: a permission rule "follows the format
   * {@code Tool} or {@code Tool(specifier)}", and {@code allowed-tools} lists TOOLS, not commands —
   * so a bare {@code git} names a tool that does not exist and pre-approves nothing, while the tool
   * the steps actually use ({@code Bash}) stays unlisted. The trailing {@code " *"} is deliberate
   * and load-bearing twice over: a rule's wildcard must sit after the subcommand (so the words
   * before it are what limit the rule), and a trailing {@code " *"} also matches the bare command,
   * which is what lets {@code Bash(./gradlew *)} cover a plain {@code ./gradlew}.
   *
   * @llmNote This is the ONLY place a platform spelling of the vocabulary is written. A second
   *     flavour (or port) adds a sibling method here, never a string built inside a renderer: the
   *     lint that pins this rendering ({@code Lints#allowedToolsViolations}) can only be true of
   *     one source.
   */
  public static String claudeToolPattern(String command) {
    return "Bash(" + command + " *)";
  }

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
