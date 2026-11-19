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

  /** A line that is nothing but three or more of the same {@code *} or {@code _} marker. */
  private static final Pattern THEMATIC_BREAK =
      Pattern.compile("^(?:\\*\\s*){3,}$|^(?:_\\s*){3,}$");

  /**
   * A line that is nothing but one or more {@code -} or one or more {@code =}, with only trailing
   * whitespace allowed.
   *
   * <p><b>@edgeCase</b> Dash and equals are not treated like {@code *}/{@code _} above: a run of
   * {@code -} is escaped at any length, not just three or more, because CommonMark reads it as a
   * setext H2 underline whenever a paragraph line precedes it — tracking "did the previous line end
   * a paragraph" would make the escape depend on neighboring lines, so every standalone dash line
   * is escaped unconditionally instead. A run of {@code =} is always a setext H1 underline, never a
   * thematic break, so the same unconditional rule applies to it too.
   */
  private static final Pattern SETEXT_OR_DASH_BREAK = Pattern.compile("^(?:-+|=+)\\s*$");

  /**
   * CommonMark allows up to this many leading spaces before a block marker still opens structure.
   */
  private static final int MAX_BLOCK_INDENT = 3;

  private MarkdownTableEscape() {}

  /**
   * Text safe inside an ordinary table cell: a pipe would start a new column.
   *
   * <p><b>@llmNote</b> The backslash is escaped before the pipe, as in every two-stage escaper —
   * escaping the pipe first would leave a backslash already in the text sitting next to the newly
   * inserted one, read back as one escaped backslash followed by a live pipe.
   */
  public static String cell(String text) {
    return collapseLineBreaks(text).replace("\\", "\\\\").replace("|", "\\|");
  }

  /**
   * Text safe inside a table cell that is about to be wrapped in a code span, where a backslash has
   * no escaping power: the pipe and the backtick are percent-encoded instead of backslash-escaped —
   * the backtick because the cell is headed into a code span of its own, the same span {@link
   * #inlineCode(String)} wraps around it, so both sinks share {@link #codeSpanEncode(String)}
   * rather than risk drifting apart on which delimiter each one remembers to neutralize.
   */
  public static String codeSpanCell(String text) {
    return codeSpanEncode(text);
  }

  /** Collapses every line break to a space: a row's text is read back as exactly one line. */
  public static String collapseLineBreaks(String text) {
    return text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
  }

  /**
   * Free-form prose safe to interpolate into the body of a generated document: a line that would
   * reopen the document's structure — a heading, a fence, a list item, a table row, a thematic
   * break or a blockquote — is escaped, line by line, so the text can only ever read as a
   * paragraph.
   *
   * <p><b>@edgeCase</b> Each leading marker character is escaped individually (<code>\#\#</code>,
   * not a single backslash before the run): a single leading backslash still leaves the unescaped
   * marker one character later in the string, which is indistinguishable from the real thing to
   * anything matching on the marker's text rather than its column.
   *
   * <p><b>@edgeCase</b> CommonMark still opens a block marker indented by up to three spaces, so
   * the indent is measured first, the marker check runs on the remainder, and the indent is
   * re-emitted ahead of the escape (<code>" ## h"</code> &rarr; <code>" \#\# h"</code>). Four or
   * more leading spaces makes the line an indented code block, which cannot open structure, so it
   * is left untouched.
   */
  public static String prose(String text) {
    return text.lines()
        .map(MarkdownTableEscape::escapeStructuralLine)
        .collect(Collectors.joining("\n"));
  }

  /**
   * Wraps text in a single-backtick inline code span, after neutralizing any backtick already in
   * the text — the same reasoning as {@link #codeSpanCell(String)}: a backslash does not survive
   * inside a code span, so an embedded backtick is percent-encoded rather than closing the span
   * early.
   */
  public static String inlineCode(String text) {
    return "`" + codeSpanEncode(text) + "`";
  }

  /**
   * The one encoding both code-span sinks share: a pipe or a backtick inside text headed into a
   * backtick-delimited span, percent-encoded because a backslash escape does not survive inside
   * one. A backslash already in the text is left alone — {@link #codeSpanCell(String)}'s B-249 pin
   * depends on it staying untouched.
   */
  private static String codeSpanEncode(String text) {
    return collapseLineBreaks(text).replace("|", "%7C").replace("`", "%60");
  }

  private static String escapeStructuralLine(String line) {
    var indent = leadingSpaceCount(line);
    if (indent > MAX_BLOCK_INDENT) {
      return line;
    }
    var indentPrefix = line.substring(0, indent);
    var rest = line.substring(indent);
    if (rest.startsWith("#")) {
      return indentPrefix + escapeLeadingRun(rest, '#');
    }
    if (rest.startsWith("```")) {
      return indentPrefix + escapeLeadingRun(rest, '`');
    }
    if (rest.startsWith("~~~")) {
      return indentPrefix + escapeLeadingRun(rest, '~');
    }
    if (rest.startsWith("|")
        || rest.startsWith(">")
        || LIST_MARKER.matcher(rest).find()
        || THEMATIC_BREAK.matcher(rest).find()
        || SETEXT_OR_DASH_BREAK.matcher(rest).matches()) {
      return indentPrefix + "\\" + rest;
    }
    return line;
  }

  /**
   * The line's run of leading ASCII spaces, uncapped — the caller decides what to do past three.
   */
  private static int leadingSpaceCount(String line) {
    var count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    return count;
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
