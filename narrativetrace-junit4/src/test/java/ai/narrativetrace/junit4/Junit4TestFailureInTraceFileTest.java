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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * A JUnit 4 test that fails on its own assertion while every traced call returns normally still
 * says what was wrong in its trace file: the header's failing-call line and a body line under the
 * result — the same message the manifest row carries.
 */
class Junit4TestFailureInTraceFileTest {

  @Test
  void theTraceFileOfAFailedTestStatesTheTestsOwnFailureInHeaderAndBody(@TempDir Path outputDir)
      throws IOException {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4FailingFixture.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var trace = Files.readString(outputDir.resolve("traces/Junit4FailingFixture/failing_test.md"));
    assertThat(trace)
        .contains(
            "Failing call: none, no recorded call threw; the test itself failed:"
                + " deliberate failure.")
        .contains("**Test failure:** deliberate failure");
  }
}
