/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-30-4 — {@code GlossarySuiteHarvest.writeGlossaryFiles} rewrites the committed {@code
 * glossary.json} and only then renders and writes {@code glossary.md}. When the second write fails,
 * the first is already on disk: the harvest reports failure to its caller while having edited a
 * committed, hand-curated file outside the build directory, and leaves the pair of artifacts that
 * one harvest produces disagreeing about the vocabulary.
 *
 * <p>INTENT: the product knows this failure mode and solved it for its other artifacts. {@code
 * TraceFileWriter.writeAtomically} exists "for an artifact a reader opens WHILE the run is still
 * going — a partial read of it is a wrong answer, not a missing one", and the glossary pair is the
 * strongest case of that rule, not an exception to it: the files are the repository's own source,
 * the extension's documentation treats writing them as something "no test run may do unasked", and
 * the caller that sees the {@code IOException} prints "Failed to write glossary" — which the
 * rewritten {@code glossary.json} contradicts.
 *
 * <p><b>@llmNote</b> The reproducer uses a directory where {@code glossary.md} belongs because that
 * is the one deterministic unwritable path on every platform the suite runs on; a read-only
 * repository checkout, a full disk and a file held open by another process reach the same line.
 */
class GlossaryPairWriteAtomicityTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);

  private static final UnaryOperator<String> PACKAGES = className -> "com.acme.billing";

  @TempDir Path glossaryDir;
  @TempDir Path outputDir;

  @Test
  @DisplayName("ADV-2026-10-30-4: a failed harvest leaves the committed glossary as it was")
  void aFailedMarkdownWriteLeavesTheCommittedJsonUnchanged() throws Exception {
    var committed = new GlossaryJsonWriter().write(curatedGlossary());
    Files.writeString(glossaryDir.resolve("glossary.json"), committed, StandardCharsets.UTF_8);
    Files.createDirectory(glossaryDir.resolve("glossary.md"));
    var harvest =
        new GlossarySuiteHarvest(
            glossaryDir, outputDir.resolve("glossary-usage.json"), PACKAGES, FIXED_CLOCK);

    assertThatThrownBy(() -> harvest.run(List.of(traceOf("ShipmentService", "dispatchShipment"))))
        .isInstanceOf(IOException.class);

    assertThat(glossaryDir.resolve("glossary.json"))
        .as("the harvest failed, so the file it had not finished writing is untouched")
        .hasContent(committed);
  }

  private static Glossary curatedGlossary() {
    return new Glossary(
        1,
        Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
        List.of(
            new GlossaryTerm(
                "overdraft account",
                "billing",
                TermKind.NOUN_PHRASE,
                TermStatus.CURATED,
                "An account allowed to go negative.",
                Map.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 8, 11))));
  }

  private static TraceTree traceOf(String className, String methodName) {
    var node =
        new TraceNode(new MethodSignature(className, methodName, List.of()), List.of(), null);
    return new DefaultTraceTree(List.of(node));
  }
}
