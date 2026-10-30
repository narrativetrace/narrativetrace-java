/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-28-4 — a run's manifest row must describe that run: {@link ScenarioManifest} promises
 * "the listing is what is actually on disk: an artifact a format or a flag did not produce is
 * absent from the row rather than listed and missing", and {@code entryFor} keeps that promise by
 * probing the layout for files that exist. It is never told which run wrote them. Change the output
 * format and run again in the same output directory — the ordinary way a developer tries {@code
 * -Dnarrativetrace.format=text} — and the previous run's artifacts are still there, so the row
 * claims the stale ones.
 *
 * <p>INTENT: the {@code trace} role is claimed by the first suffix that exists, and {@code .md}
 * comes before {@code .txt} in the probe order. A text-format run therefore publishes a row, a
 * {@code RunIndex} link and a {@code RunPointers} console pointer naming the <em>Markdown</em>
 * trace of an earlier run, while the trace it just wrote is the file nobody is pointed at. The
 * Markdown run's diagram, JSON and structural companions are listed as this run's too, although a
 * text run writes none of the three.
 *
 * <p><b>@llmNote</b> Nothing cleans a scenario's artifacts between runs: the output directory is
 * not a declared Gradle task output, and a write only ever creates the files of the format it was
 * asked for. The two candidate fixes sit in different places — have the write remove the other
 * formats' artifacts for the identity it is writing, or pass the run's format into {@code entryFor}
 * so the probe cannot claim a file this run did not produce. The second alone still leaves a stale
 * trace on disk for the next reader who globs the directory.
 */
class ManifestStaleFormatArtifactTest {

  private static final ArtifactIdentity IDENTITY =
      ArtifactIdentity.ofMethod("com.example.OrderTest", "tripSettles");

  private static final NarrativeRenderer DIAGRAM = tree -> "sequenceDiagram";

  @Test
  @DisplayName("ADV-2026-10-28-4: a row names the trace this run wrote, not the last run's")
  void aFormatSwitchLeavesTheRowPointingAtThePreviousRunsTrace(@TempDir Path dir) throws Exception {
    write(dir, "markdown");

    write(dir, "text");

    var row = ScenarioManifest.entryFor(dir, IDENTITY, "trip settles");
    assertThat(row.artifacts().get("trace")).endsWith(".txt");
  }

  @Test
  @DisplayName("ADV-2026-10-28-4: a row lists no artifact the run's format did not produce")
  void aTextRunDoesNotClaimTheMarkdownRunsCompanionArtifacts(@TempDir Path dir) throws Exception {
    write(dir, "markdown");

    write(dir, "text");

    var row = ScenarioManifest.entryFor(dir, IDENTITY, "trip settles");
    assertThat(row.artifacts()).doesNotContainKeys("diagram", "json", "structural");
  }

  private static void write(Path outputDir, String format) throws Exception {
    TraceTestSupport.writeTraceFile(
        IDENTITY,
        "trip settles",
        traceWithOneCall(),
        false,
        outputDir,
        new PrintStream(new ByteArrayOutputStream()),
        format,
        DIAGRAM,
        DIAGRAM);
  }

  private static TraceTree traceWithOneCall() {
    var node =
        new TraceNode(
            new MethodSignature("Service", "doWork", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    return new DefaultTraceTree(List.of(node));
  }
}
