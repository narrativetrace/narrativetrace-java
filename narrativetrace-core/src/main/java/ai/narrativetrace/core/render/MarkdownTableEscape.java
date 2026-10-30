/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The one rule for text placed in a GitHub-flavoured Markdown table: a pipe starts a new column and
 * a line break starts a new row, so every emitter that writes a table cell from a raw name, message
 * or path routes through here instead of re-deriving the rule.
 *
 * <p>INTENT: {@code RunIndex}, {@code GlossaryMarkdownRenderer} and {@code ClarityReportRenderer}
 * each wrote their own version of this escape, and two of the three left the line-break half out —
 * exactly the half a GFM table needs, since a row's leading pipe is optional and an unescaped
 * newline is read back as a second row. One shared helper is the fix for the drift, not just the
 * missing character.
 *
 * <p><b>@llmNote</b> Two sinks, because a backslash escape does not survive an inline code span: a
 * table cell's own text uses {@link #cell(String)} ({@code \|}), while text that is about to be
 * wrapped in backticks uses {@link #codeSpanCell(String)} ({@code %7C}) — see {@code
 * RunIndex#path}, the convention this generalizes. {@link #prose(String)} and {@link
 * #inlineCode(String)} generalize it again, to free-form text interpolated into the document's body
 * rather than a table: a captured or configured description, not a name a caller already controls.
 */
public final class MarkdownTableEscape {

  /** A line beginning an unordered or ordered list item. */
  private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*+]|\\d+[.)])(?:\\s|$)");

  private MarkdownTableEscape() {}

  /** Text safe inside an ordinary table cell: a pipe would start a new column. */
  public static String cell(String text) {
    return collapseLineBreaks(text).replace("|", "\\|");
  }

  /**
   * Text safe inside a table cell that is about to be wrapped in a code span, where a backslash has
   * no escaping power: the pipe is percent-encoded instead of backslash-escaped.
   */
  public static String codeSpanCell(String text) {
    return collapseLineBreaks(text).replace("|", "%7C");
  }

  /** Collapses every line break to a space: a row's text is read back as exactly one line. */
  public static String collapseLineBreaks(String text) {
    return text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
  }

  /**
   * Free-form prose safe to interpolate into the body of a generated document: a line that would
   * reopen the document's structure — a heading, a fence, a list item or a table row — is escaped,
   * line by line, so the text can only ever read as a paragraph.
   *
   * <p><b>@edgeCase</b> Each leading marker character is escaped individually (<code>\#\#</code>,
   * not a single backslash before the run): a single leading backslash still leaves the unescaped
   * marker one character later in the string, which is indistinguishable from the real thing to
   * anything matching on the marker's text rather than its column.
   */
  public static String prose(String text) {
    return text.lines()
        .map(MarkdownTableEscape::escapeStructuralLine)
        .collect(Collectors.joining("\n"));
  }

  /**
   * Wraps text in a single-backtick inline code span, after neutralizing any backtick already in
   * the text — the same reasoning as {@link #codeSpanCell(String)}, extended to the delimiter
   * itself: a backslash does not survive inside a code span, so an embedded backtick is
   * percent-encoded rather than closing the span early.
   */
  public static String inlineCode(String text) {
    return "`" + codeSpanCell(text).replace("`", "%60") + "`";
  }

  private static String escapeStructuralLine(String line) {
    if (line.startsWith("#")) {
      return escapeLeadingRun(line, '#');
    }
    if (line.startsWith("```")) {
      return escapeLeadingRun(line, '`');
    }
    if (line.startsWith("~~~")) {
      return escapeLeadingRun(line, '~');
    }
    if (line.startsWith("|") || LIST_MARKER.matcher(line).find()) {
      return "\\" + line;
    }
    return line;
  }

  /** Escapes a line's leading run of {@code marker}, one backslash per character. */
  private static String escapeLeadingRun(String line, char marker) {
    int end = 0;
    while (end < line.length() && line.charAt(end) == marker) {
      end++;
    }
    var escaped = new StringBuilder();
    for (int i = 0; i < end; i++) {
      escaped.append('\\').append(marker);
    }
    return escaped.append(line, end, line.length()).toString();
  }
}
