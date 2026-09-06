/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Escapers for text interpolated into Markdown output.
 *
 * <p>INTENT: {@link MarkdownRenderer} embeds rendered trace values, exception messages, error
 * context, and narration — any of which may carry attacker-influenced content (exception messages
 * commonly echo user input) — into a Markdown document that is later viewed in a browser or
 * preview. This helper is the single place that neutralizes characters capable of injecting active
 * markup.
 *
 * <p><b>@edgeCase</b> Both sinks first run the text through {@link ControlEscape}. Every site that
 * interpolates here is a single line — an error line, an error-context line, an italic narration
 * line, an inline code span — so a raw line break is markup: it ends the blockquote or the code
 * span and starts whatever follows as document structure. An exception message with a newline and a
 * fence in it opened a fenced code block from inside a value; exception messages commonly echo user
 * input, which is exactly the reason this class exists. Control characters render as the same inert
 * escapes the rest of the product uses, so the text stays on its line and stays readable.
 *
 * <p><b>@llmNote</b> Markdown has two relevant sinks and they escape differently:
 *
 * <ul>
 *   <li>{@link #text(String)} — prose placed as <em>active</em> Markdown (e.g. an exception message
 *       in a blockquote). HTML passes through Markdown, so {@code &}, {@code <}, {@code >} are
 *       HTML-escaped to entities to stop {@code <img onerror=...>}-style injection.
 *   <li>{@link #code(String)} — content inside an inline code span. There, {@code <} and {@code >}
 *       are inert; the only breakout is a backtick matching the delimiter. Kept for the code-span
 *       sites.
 * </ul>
 */
final class MarkdownEscape {

  private MarkdownEscape() {}

  /**
   * Neutralizes control characters, then HTML-escapes {@code &}, {@code <} and {@code >} so
   * interpolated prose cannot introduce active HTML when the Markdown is rendered to a
   * browser/preview. Text carrying none of them is returned unchanged.
   *
   * @param text the raw prose (e.g. an exception message or narration)
   * @return the text with control characters rendered inert and {@code &}, {@code <}, {@code >}
   *     replaced by their HTML entities
   */
  static String text(String text) {
    var safe = ControlEscape.sanitize(text);
    var sb = new StringBuilder(safe.length());
    for (int i = 0; i < safe.length(); i++) {
      char c = safe.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Wraps {@code content} in an inline code span whose backtick fence is longer than any run of
   * backticks inside it, so the content cannot terminate the span early and leak following text
   * into active markup. When the fence is widened, one space of padding is added on each side
   * (CommonMark strips a single leading/trailing space), keeping the content faithful. Content with
   * no backtick is wrapped in a single-backtick span, byte-identical to the previous hand-written
   * form. Control characters are rendered inert first, so a line break cannot end the span either.
   *
   * @param content the raw value to place inside a code span (e.g. a rendered parameter or return
   *     value)
   * @return the full code span including its delimiters
   */
  static String code(String content) {
    var safe = ControlEscape.sanitize(content);
    int maxRun = longestBacktickRun(safe);
    if (maxRun == 0) {
      return "`" + safe + "`";
    }
    var fence = "`".repeat(maxRun + 1);
    return fence + " " + safe + " " + fence;
  }

  private static int longestBacktickRun(String s) {
    int max = 0;
    int run = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '`') {
        run++;
        max = Math.max(max, run);
      } else {
        run = 0;
      }
    }
    return max;
  }
}
