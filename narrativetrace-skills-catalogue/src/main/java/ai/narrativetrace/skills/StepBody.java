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
 * What a step actually does: either a sequence of shell {@link CommandStep commands} in the port's
 * closed vocabulary, or a {@link CodeStep code} snippet the reader writes/copies. Every step is
 * expressible as exactly one of these — replayability principle 5 (never leave the agent to guess
 * at unstated steps).
 */
public sealed interface StepBody {

  /** One or more commands, first token per command in {@link CommandVocabulary#JAVA}. */
  record CommandStep(List<String> commands) implements StepBody {
    public CommandStep {
      commands = List.copyOf(commands);
    }
  }

  /** A code snippet the reader adds, in the given language, embedded verbatim (no file path). */
  record CodeStep(String language, String code) implements StepBody {
    public CodeStep {
      if (language == null || language.isBlank()) {
        throw new IllegalArgumentException("a CodeStep's language must not be blank");
      }
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("a CodeStep's code must not be blank");
      }
    }
  }

  /**
   * A code snippet embedded FROM a real, tested source file at render time — never typed twice.
   * {@code path} is repo-root-relative (e.g. {@code
   * "sixty-seconds/src/main/java/com/example/orders/Main.java"}); the catalogue names the path, the
   * renderer reads the file's current content, so the two can never drift the way a duplicated
   * literal can (rule 8, docs as tests). {@link
   * ai.narrativetrace.skills.render.ClaudeSkillRenderer} also wraps the rendered block in the same
   * {@code <!-- snippet: path -->} / {@code <!-- /snippet -->} markers the documentation pages use,
   * so the root {@code snippetCheck}/{@code snippetSync} tasks (see {@code SnippetSupport}) verify
   * the committed {@code SKILL.md} against the source the same way they already verify {@code
   * documentation/sixty-seconds.md}.
   */
  record SnippetStep(String language, String path) implements StepBody {
    public SnippetStep {
      if (language == null || language.isBlank()) {
        throw new IllegalArgumentException("a SnippetStep's language must not be blank");
      }
      if (path == null || path.isBlank()) {
        throw new IllegalArgumentException("a SnippetStep's path must not be blank");
      }
    }
  }
}
