/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * A trace file reports the loss of its own test, never the suite context's running total (B-342).
 *
 * <p>INTENT: Since §176 one context serves every JUnit 4 class rule in the JVM, and its loss
 * counters only grow. The linked rule brackets them per test for the suite footer and the approval
 * verdict, but the tree a test's file is rendered from carries the context's reading as {@code
 * captureTrace()} returned it — so once any test has shed, every later file says {@code incomplete:
 * true} with that test's count, including a file whose own test shed nothing.
 */
class Junit4TraceLossPerTestTest {

  @Test
  void aTestThatShedNothingIsNotReportedIncompleteAfterANeighbourShed(@TempDir Path outputDir)
      throws Exception {
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4SheddingNeighbourFixture.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(trace(outputDir, "first_sheds_under_load"))
        .as("the test that overflowed the ring says so")
        .contains("incomplete: true");
    assertThat(trace(outputDir, "second_makes_one_call"))
        .as("the one-call test after it shed nothing of its own")
        .doesNotContain("incomplete: true")
        .doesNotContain("dropped_events:")
        .doesNotContain("Incomplete narrative");
  }

  private static String trace(Path outputDir, String slug) throws Exception {
    return Files.readString(
        outputDir
            .resolve("traces")
            .resolve("Junit4SheddingNeighbourFixture")
            .resolve(slug + ".md"));
  }
}
