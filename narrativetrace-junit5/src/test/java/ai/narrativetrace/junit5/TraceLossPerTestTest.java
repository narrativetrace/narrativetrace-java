/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * A trace file reports the loss of its own test, never the suite's running total (B-342).
 *
 * <p>INTENT: Since §176 one context serves the whole suite, and its loss counters only grow. The
 * extension brackets them per test for the console and the approval verdict, but the tree a test's
 * file is rendered from carries the context's reading as {@code captureTrace()} returned it — so
 * once any test has shed, every later file says {@code incomplete: true} with that test's count,
 * including a file whose own test shed nothing.
 */
class TraceLossPerTestTest {

  @Test
  void aTestThatShedNothingIsNotReportedIncompleteAfterANeighbourShed(@TempDir Path outputDir)
      throws Exception {
    runSuite(outputDir);

    var shedder = trace(outputDir, "sheds_under_load");
    var neighbour = trace(outputDir, "makes_one_call");
    assertThat(shedder)
        .as("the test that overflowed its ring says so")
        .contains("incomplete: true");
    assertThat(neighbour)
        .as("the one-call test after it shed nothing of its own")
        .doesNotContain("incomplete: true")
        .doesNotContain("dropped_events:")
        .doesNotContain("Incomplete narrative");
  }

  private static String trace(Path outputDir, String slug) throws Exception {
    return Files.readString(
        outputDir.resolve("traces").resolve("SheddingNeighbourFixture").resolve(slug + ".md"));
  }

  private static void runSuite(Path outputDir) {
    LauncherFactory.create()
        .execute(
            LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(SheddingNeighbourFixture.class))
                .configurationParameter(NarrativeTraceExtension.BUFFER_CAPACITY_KEY, "16")
                .configurationParameter("narrativetrace.outputDir", outputDir.toString())
                .build());
  }
}
