/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * INTENT: what {@link EvalRunner} actually hands the agent for turn 1 — never {@code prompt.md}'s
 * raw bytes when the file is a CASE DOCUMENT, because a case document's "Expected trajectory" and
 * "Grading" sections describe what the grader checks, and sending them is sending the grader's own
 * rubric to the agent it grades.
 *
 * <p>A {@code prompt.md} is one of two kinds, decided by its first line alone:
 *
 * <ul>
 *   <li>{@code # Case: <name>} — a {@link Kind#CASE_DOCUMENT}. Only the {@code >} blockquote
 *       directly under its {@code **Task prompt} line is the agent's prompt; everything else in the
 *       file is this repository's own documentation of the case.
 *   <li>anything else — a {@link Kind#BARE_PROMPT}, sent byte for byte. The init-prompt cases are
 *       bare prompts on purpose: their hard wraps are part of the text this project publishes and
 *       replays.
 * </ul>
 *
 * @llmNote A case document with no {@code **Task prompt} blockquote THROWS rather than falling back
 *     to the whole file — that fall-back is exactly the leak this class exists to close, so
 *     degrading to it on a malformed case document would reopen it silently.
 */
public final class CasePrompt {

  private static final String CASE_DOCUMENT_MARKER = "# Case:";
  private static final String TASK_PROMPT_MARKER = "**Task prompt";

  private CasePrompt() {}

  /** The two shapes a {@code prompt.md} can take; see the class doc for what each sends. */
  public enum Kind {
    /** {@code # Case:} on the first line — this repository's own case documentation. */
    CASE_DOCUMENT,
    /** Any other first line — the user's own words, sent byte for byte. */
    BARE_PROMPT
  }

  /** {@link Kind#CASE_DOCUMENT} iff {@code text}'s first line starts with {@code # Case:}. */
  public static Kind kind(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    int firstNewline = text.indexOf('\n');
    String firstLine = firstNewline < 0 ? text : text.substring(0, firstNewline);
    return firstLine.startsWith(CASE_DOCUMENT_MARKER) ? Kind.CASE_DOCUMENT : Kind.BARE_PROMPT;
  }

  /**
   * What the agent is actually sent for {@code path}'s {@code prompt.md}: {@code text} itself for a
   * bare prompt, or the unwrapped {@code >} blockquote under {@code **Task prompt} for a case
   * document.
   *
   * @throws IllegalArgumentException if {@code text} is a case document with no such blockquote,
   *     naming {@code path} — never a silent fall-back to {@code text} itself
   */
  public static String taskPrompt(Path path, String text) {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    return kind(text) == Kind.BARE_PROMPT ? text : blockquoteUnderTaskPrompt(path, text);
  }

  private static String blockquoteUnderTaskPrompt(Path path, String text) {
    List<String> lines = text.lines().toList();
    int taskPromptLine = indexOfTaskPromptLine(lines);
    if (taskPromptLine < 0) {
      throw new IllegalArgumentException(
          "case document with no \"" + TASK_PROMPT_MARKER + "\" line: " + path);
    }
    int at = taskPromptLine + 1;
    while (at < lines.size() && lines.get(at).isBlank()) {
      at++;
    }
    if (at >= lines.size() || !lines.get(at).startsWith(">")) {
      throw new IllegalArgumentException(
          "case document with no \">\" blockquote under \"" + TASK_PROMPT_MARKER + "\": " + path);
    }
    return joinedParagraphs(lines, at);
  }

  private static int indexOfTaskPromptLine(List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith(TASK_PROMPT_MARKER)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Every {@code >} line from {@code at} onward, unwrapped into paragraphs: a wrapped line joins
   * the paragraph it continues with one space, an empty {@code >} line closes the current
   * paragraph, and the closed paragraphs are rejoined with one blank line between them.
   */
  private static String joinedParagraphs(List<String> lines, int at) {
    List<String> paragraphs = new ArrayList<>();
    StringBuilder paragraph = new StringBuilder();
    for (int i = at; i < lines.size() && lines.get(i).startsWith(">"); i++) {
      String quoted = unquote(lines.get(i));
      if (quoted.isEmpty()) {
        closeParagraph(paragraphs, paragraph);
      } else {
        if (paragraph.length() > 0) {
          paragraph.append(' ');
        }
        paragraph.append(quoted);
      }
    }
    closeParagraph(paragraphs, paragraph);
    return String.join("\n\n", paragraphs);
  }

  private static void closeParagraph(List<String> paragraphs, StringBuilder paragraph) {
    if (paragraph.length() > 0) {
      paragraphs.add(paragraph.toString());
      paragraph.setLength(0);
    }
  }

  /**
   * {@code "> text"} to {@code "text"}; a bare {@code ">"} (or {@code "> "}) to the empty string.
   */
  private static String unquote(String line) {
    String rest = line.substring(1);
    return rest.startsWith(" ") ? rest.substring(1) : rest;
  }
}
