/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-11-06-1 — {@code NarrativeTraceListener.artifactIdentity} numbers a data-driven
 * invocation by its data-provider row, so a method that is data-driven <em>and</em> repeated
 * ({@code @Test(dataProvider = ..., invocationCount = n)}) hands the same index to every run of a
 * row: n invocations share one artifact identity and the last one to finish overwrites the rest.
 *
 * <p>INTENT: {@code ArtifactIdentity} is the family's master naming scheme and says what it is for
 * — "two invocations of one method always differ in the index ... the index is what makes the
 * scheme collision-proof". It exists because one invocation's artifacts used to overwrite another's
 * and one invocation's {@code .approved.nt} baseline used to judge another's structure. The row
 * index satisfies that contract only while a row runs once; combined with a repetition count it is
 * not an invocation number at all.
 *
 * <p><b>@llmNote</b> Suspected cause {@code NarrativeTraceListener#artifactIdentity} — {@code
 * method.isDataDriven()} selects {@code TestResult#getParameterIndex() + 1} and so ignores {@code
 * getInvocationCount()}, which the branch above it has already read. The start-order counter
 * ({@code TestNgRun#nextInvocation}) is the identity that stays unique for both.
 */
class RepeatedRowArtifactCollisionTest {

  @Test
  @DisplayName(
      "ADV-2026-11-06-1: a repeated data-provider row may not reuse another run's artifact")
  void everyInvocationOfARepeatedRowOwnsItsOwnArtifact(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, RepeatedRowFixture.class);

    assertThat(
            TestNgRuns.listing(outputDir).stream()
                .filter(f -> f.startsWith("traces/") && f.endsWith(".md"))
                .toList())
        .as("two rows run twice each is four invocations, so four traces")
        .hasSize(4);
  }

  @Test
  @DisplayName("ADV-2026-11-06-1: no invocation's trace is unreachable through the written files")
  void theTraceOfEveryInvocationIsReadableAfterTheRun(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, RepeatedRowFixture.class);

    var traced = new TreeSet<String>();
    for (var file : TestNgRuns.listing(outputDir)) {
      if (file.startsWith("traces/")) {
        var matcher =
            Pattern.compile("(?:kayak|tent)-\\d+")
                .matcher(Files.readString(outputDir.resolve(file)));
        while (matcher.find()) {
          traced.add(matcher.group());
        }
      }
    }
    assertThat(traced)
        .as("each invocation traced a value of its own; every one reached a file")
        .hasSize(4);
  }

  @Test
  @DisplayName("ADV-2026-11-06-1: no two manifest rows may name the same trace artifact")
  void theManifestRowsNameAsManyArtifactsAsThereAreInvocations(@TempDir Path outputDir)
      throws Exception {
    TestNgRuns.run(outputDir, RepeatedRowFixture.class);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    var rows = new ArrayList<String>();
    var matcher = Pattern.compile("\"trace\": \"([^\"]+)\"").matcher(manifest);
    while (matcher.find()) {
      rows.add(matcher.group(1));
    }
    assertThat(rows)
        .as("a row a reader cannot tell from another points at evidence that is not its own")
        .doesNotHaveDuplicates();
  }
}
