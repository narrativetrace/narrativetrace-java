/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.core.render.MarkdownTableEscape;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code INDEX.md}: the one file an agent (or a person) opens first in a trace output directory.
 *
 * <p>INTENT: a run leaves hundreds of files and the manifest is JSON for machines. The index says
 * in a paragraph what the directory is, then lists the failing tests first with the trace to open
 * and the error, then the passing ones. It is written from the same rows, at the same moment, as
 * {@code manifest.json}.
 *
 * <p><b>@llmNote</b> Failing rows keep execution order, then passing rows keep theirs, so the file
 * is deterministic for a given run. Paths are relative to the index, which lives in the output
 * directory beside {@code manifest.json}. The error column is the manifest row's message, which
 * already went through the exception-message filter; here it is only reduced to one table cell. A
 * row with no recorded verdict is listed under Passing with its outcome shown as {@code unknown}.
 *
 * <p><b>@sideEffects</b> Written through a temporary file and moved into place, so a reader never
 * finds a half-written index.
 */
public final class RunIndex {

  /** The index's name inside the output directory. */
  public static final String FILE_NAME = "INDEX.md";

  private static final int MESSAGE_LIMIT = 200;

  private RunIndex() {}

  /** Writes {@code INDEX.md}; writes nothing when the run traced no scenario. */
  public static void write(List<ScenarioManifest.Entry> entries, Path outputDir)
      throws IOException {
    if (entries.isEmpty()) {
      return;
    }
    new TraceFileWriter().writeAtomically(render(entries), outputDir.resolve(FILE_NAME));
  }

  /** The index document. */
  public static String render(List<ScenarioManifest.Entry> entries) {
    var failing = entries.stream().filter(ScenarioManifest.Entry::failed).toList();
    var passing = entries.stream().filter(entry -> !entry.failed()).toList();
    var sb = new StringBuilder();
    sb.append("# NarrativeTrace run index\n\n");
    sb.append(
        "This directory holds the recorded call trees of one test run, one trace file per test."
            + " Each trace shows, as indentation, which call made which, with every call's"
            + " parameters and its return value or thrown exception as rendered values;"
            + " `[REDACTED]` marks a redacted value. `manifest.json` lists every artifact of every"
            + " test. Start with the Failing table below: open the trace of a failing test, and"
            + " read down to the first call that threw.\n\n");
    sb.append("## Failing\n\n");
    if (failing.isEmpty()) {
      sb.append("No test failed in this run.\n\n");
    } else {
      sb.append("| Test | Outcome | Trace | Error |\n|---|---|---|---|\n");
      failing.forEach(entry -> sb.append(failingRow(entry)));
      sb.append("\n");
    }
    sb.append("## Passing\n\n");
    if (passing.isEmpty()) {
      sb.append("No test passed in this run.\n");
    } else {
      sb.append("| Test | Outcome | Trace |\n|---|---|---|\n");
      passing.forEach(entry -> sb.append(passingRow(entry)));
    }
    return sb.toString();
  }

  private static String failingRow(ScenarioManifest.Entry entry) {
    return "| "
        + cell(RunPointers.label(entry.identity()))
        + " | "
        + cell(entry.outcome())
        + " | "
        + path(entry)
        + " | "
        + cell(oneLine(entry.message()))
        + " |\n";
  }

  private static String passingRow(ScenarioManifest.Entry entry) {
    return "| "
        + cell(RunPointers.label(entry.identity()))
        + " | "
        + cell(entry.outcome() == null ? "unknown" : entry.outcome())
        + " | "
        + path(entry)
        + " |\n";
  }

  private static String path(ScenarioManifest.Entry entry) {
    var trace = entry.artifacts().get("trace");
    return trace == null ? "(no trace file)" : "`" + MarkdownTableEscape.codeSpanCell(trace) + "`";
  }

  /** The message as one short line: whitespace collapsed, capped. */
  private static String oneLine(String message) {
    if (message == null) {
      return "";
    }
    var line = message.replaceAll("\\s+", " ").strip();
    return line.length() <= MESSAGE_LIMIT ? line : line.substring(0, MESSAGE_LIMIT) + "…";
  }

  /** Text safe inside a table cell: a pipe would start a new column. */
  private static String cell(String text) {
    return text == null ? "" : MarkdownTableEscape.cell(text);
  }
}
