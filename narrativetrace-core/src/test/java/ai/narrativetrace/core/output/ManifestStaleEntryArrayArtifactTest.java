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
 * ADV-2026-10-28-7 — sibling of {@link ManifestStaleFormatArtifactTest} (B-224): {@code
 * .canonical.json} and {@code .structural.json} are written by {@code writeCanonicalTraceFile} /
 * {@code writeStructuralTraceFile}, called by each integration only while {@code
 * narrativetrace.canonicalJson} / {@code narrativetrace.structuralJson} is on — not by the common
 * write path B-224 taught to clean stale siblings. A run with a flag on, then a run for the same
 * identity with the flag off, leaves the earlier entry array on disk for {@link
 * ScenarioManifest#entryFor} to probe and claim as this run's, although this run never asked for
 * it.
 *
 * <p><b>@llmNote</b> The entry-array write is a second, independent call an integration makes
 * beside {@code writeTraceFile} — unlike the four trace formats, there is no "this run's format" to
 * compare against, only "this run did or did not ask for it". The fix removes both entry arrays
 * unconditionally as part of the same stale-artifact cleanup {@code writeTraceFile} already runs
 * before every write; the caller's own conditional then rewrites whichever one this run's flags
 * turned on.
 */
class ManifestStaleEntryArrayArtifactTest {

  private static final ArtifactIdentity IDENTITY =
      ArtifactIdentity.ofMethod("com.example.OrderTest", "tripSettles");

  private static final ArtifactIdentity OTHER_IDENTITY =
      ArtifactIdentity.ofMethod("com.example.OrderTest", "tripCancels");

  private static final NarrativeRenderer DIAGRAM = tree -> "sequenceDiagram";

  @Test
  @DisplayName("ADV-2026-10-28-7: a canonical entry array does not outlive its flag")
  void aCanonicalEntryArrayDoesNotOutliveItsFlag(@TempDir Path dir) throws Exception {
    writeTrace(dir, IDENTITY);
    TraceTestSupport.writeCanonicalTraceFile(IDENTITY, traceWithOneCall(), dir);

    writeTrace(dir, IDENTITY);

    var row = ScenarioManifest.entryFor(dir, IDENTITY, "trip settles");
    assertThat(row.artifacts()).doesNotContainKey("canonicalJson");
  }

  @Test
  @DisplayName("ADV-2026-10-28-7: a structural entry array does not outlive its flag")
  void aStructuralEntryArrayDoesNotOutliveItsFlag(@TempDir Path dir) throws Exception {
    writeTrace(dir, IDENTITY);
    TraceTestSupport.writeStructuralTraceFile(IDENTITY, traceWithOneCall(), dir);

    writeTrace(dir, IDENTITY);

    var row = ScenarioManifest.entryFor(dir, IDENTITY, "trip settles");
    assertThat(row.artifacts()).doesNotContainKey("structuralJson");
  }

  @Test
  @DisplayName("ADV-2026-10-28-7: another identity's entry array survives the cleanup")
  void anotherIdentitysEntryArraySurvivesTheCleanup(@TempDir Path dir) throws Exception {
    writeTrace(dir, OTHER_IDENTITY);
    TraceTestSupport.writeCanonicalTraceFile(OTHER_IDENTITY, traceWithOneCall(), dir);

    writeTrace(dir, IDENTITY);
    TraceTestSupport.writeCanonicalTraceFile(IDENTITY, traceWithOneCall(), dir);
    writeTrace(dir, IDENTITY);

    var otherRow = ScenarioManifest.entryFor(dir, OTHER_IDENTITY, "trip cancels");
    assertThat(otherRow.artifacts()).containsKey("canonicalJson");
  }

  private static void writeTrace(Path outputDir, ArtifactIdentity identity) throws Exception {
    TraceTestSupport.writeTraceFile(
        identity,
        "trip settles",
        traceWithOneCall(),
        false,
        outputDir,
        new PrintStream(new ByteArrayOutputStream()),
        "markdown",
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
