/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A trace file reports the loss of its own test, never the shared pipeline's running total (B-342).
 *
 * <p>INTENT: One pipeline serves every TestNG test in the JVM, and its loss counters only grow. The
 * listener brackets them per test for the run's footer and the approval verdict, but the tree a
 * test's file is rendered from carries the context's reading as {@code captureTrace()} returned it
 * — so once any test has shed, every later file says {@code incomplete: true} with that test's
 * count, including a file whose own test shed nothing.
 */
class TraceLossPerTestTest {

  @Test
  void aTestThatShedNothingIsNotReportedIncompleteAfterANeighbourShed(@TempDir Path outputDir)
      throws Exception {
    TestNgRuns.run(outputDir, SheddingNeighbourFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "SheddingNeighbourFixture", "sheds_under_load"))
        .as("the test that overflowed the ring says so")
        .contains("incomplete: true");
    assertThat(TestNgRuns.trace(outputDir, "SheddingNeighbourFixture", "makes_one_call"))
        .as("the one-call test after it shed nothing of its own")
        .doesNotContain("incomplete: true")
        .doesNotContain("dropped_events:")
        .doesNotContain("Incomplete narrative");
  }
}
