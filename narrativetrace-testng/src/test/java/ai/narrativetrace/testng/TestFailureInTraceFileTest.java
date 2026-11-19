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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A TestNG test that fails on its own assertion while every traced call returns normally still says
 * what was wrong in its trace file: the header's failing-call line and a body line under the result
 * — the same message the manifest row carries.
 */
class TestFailureInTraceFileTest {

  @Test
  void theTraceFileOfAFailedTestStatesTheTestsOwnFailureInHeaderAndBody(@TempDir Path outputDir)
      throws Exception {
    var run = TestNgRuns.run(outputDir, AssertionFailureFixture.class);

    assertThat(run.failed()).isTrue();
    var trace =
        Files.readString(
            outputDir.resolve(
                "traces/AssertionFailureFixture/"
                    + "pricing_total_disagrees_with_the_reference_basket.md"));
    assertThat(trace)
        .contains(
            "Failing call: none, no recorded call threw; the test itself failed:"
                + " total for the reference basket expected [999] but found [200].")
        .contains(
            "**Test failure:** total for the reference basket expected [999] but found [200]");
  }
}
