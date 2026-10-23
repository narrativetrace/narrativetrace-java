/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.util.function.Function;

/**
 * The few self-describing lines at the top of a written trace file.
 *
 * <p>INTENT: an agent that opens a trace has to be told, in the file itself, what it is looking at:
 * the recorded call tree of one test, how to read the indentation, where the call that failed is,
 * and where the run's index and manifest are. Seven lines at most, as a comment, so a reader that
 * ignores comments sees the trace exactly as before.
 *
 * <p><b>@llmNote</b> Applied only to the files an integration WRITES, behind {@link
 * TraceTestSupport#writeTraceFile}; the renderers and exporters stay byte-for-byte what they were,
 * so the canonical wire form, the structural {@code .nt} artifact and every renderer test are
 * untouched. The failing call is located in the finished text: the first line carrying the format's
 * thrown-exception marker, numbered in the file as written, header included.
 *
 * <p><b>@edgeCase</b> Markdown keeps its YAML frontmatter on line 1 and the header follows it as an
 * HTML comment; text gets {@code #} comment lines. The Mermaid and PlantUML files are not headed (a
 * diagram tool expects its own first line), and neither is the per-test {@code .json} companion: it
 * is validated on disk against the cross-runtime {@code chapter-tree.schema.json}, whose root
 * allows no extra property, and that schema is a contract every runtime mirrors byte for byte.
 */
public final class TraceFileHeader {

  /** Marks a thrown outcome in the Markdown trace. */
  private static final String MARKDOWN_THREW = "❌";

  /** Marks a thrown outcome in the text trace. */
  private static final String TEXT_THREW = "!!";

  private static final String NONE = "none, no call threw in this run";

  private TraceFileHeader() {}

  /**
   * What the header says about one file.
   *
   * @param test {@code Class.method} of the test
   * @param outcome {@code passed} or {@code failed}
   * @param durationMillis the scenario's duration, already formatted
   * @param indexPath the run index, relative to this file
   * @param manifestPath the run manifest, relative to this file
   */
  public record Facts(
      String test, String outcome, String durationMillis, String indexPath, String manifestPath) {}

  /**
   * The trace file for {@code format}: text and Markdown (anything that is not a diagram format)
   * get the header; a Mermaid or PlantUML file is returned as it is, since a diagram tool expects
   * its own first line ({@code sequenceDiagram}, {@code @startuml}).
   */
  public static String apply(String format, String content, Facts facts) {
    return switch (format.toLowerCase()) {
      case "text" -> commented(content, facts, "# ", TEXT_THREW);
      case "mermaid", "plantuml" -> content;
      default -> markdown(content, facts);
    };
  }

  private static String markdown(String content, Facts facts) {
    var close = content.startsWith("---\n") ? content.indexOf("\n---\n", 3) : -1;
    var split = close < 0 ? 0 : close + "\n---\n".length();
    return assemble(
        content.substring(0, split),
        content.substring(split),
        MARKDOWN_THREW,
        where -> "<!--\n" + body(facts, where, "") + "-->\n");
  }

  private static String commented(String content, Facts facts, String prefix, String marker) {
    return assemble("", content, marker, where -> body(facts, where, prefix));
  }

  private static String body(Facts facts, String where, String prefix) {
    return prefix
        + "NarrativeTrace trace file\n"
        + prefix
        + "What this is: the recorded call tree of one test run: "
        + facts.test()
        + ", "
        + facts.outcome()
        + ", "
        + facts.durationMillis()
        + " ms.\n"
        + prefix
        + "How to read it: indentation is call nesting; each line is one call with its"
        + " parameters and its return value or thrown exception, as rendered values."
        + " [REDACTED] marks a redacted value.\n"
        + prefix
        + "Failing call: "
        + where
        + ".\n"
        + prefix
        + "Run index: "
        + facts.indexPath()
        + ". Artifact list: "
        + facts.manifestPath()
        + " (both are written when the run ends).\n";
  }

  /**
   * The header placed between {@code prefix} and {@code rest}, naming the first line of {@code
   * rest} that carries {@code marker} by its number in the whole file.
   *
   * <p>The header's own length cannot depend on the number it states, so it is measured once with a
   * placeholder and the line number is computed from that.
   */
  private static String assemble(
      String prefix, String rest, String marker, Function<String, String> header) {
    var headerLines = newlines(header.apply("x"));
    var where = NONE;
    var index = firstLineWith(rest, marker);
    if (index >= 0) {
      where =
          "line "
              + (newlines(prefix) + headerLines + index + 1)
              + " of this file (the first call that threw)";
    }
    return prefix + header.apply(where) + rest;
  }

  private static int firstLineWith(String text, String marker) {
    var lines = text.split("\n", -1);
    for (var i = 0; i < lines.length; i++) {
      if (lines[i].contains(marker)) {
        return i;
      }
    }
    return -1;
  }

  private static int newlines(String text) {
    return (int) text.chars().filter(c -> c == '\n').count();
  }
}
