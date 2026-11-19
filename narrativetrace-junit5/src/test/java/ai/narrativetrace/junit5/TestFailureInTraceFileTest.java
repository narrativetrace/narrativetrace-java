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
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * A test that fails on its own assertion, with every traced call returning normally, still says
 * what was wrong in the trace file a reader opens: the header's failing-call line and a body line
 * under the result.
 */
class TestFailureInTraceFileTest {

  private static final String MESSAGE =
      "total for the reference basket ==> expected: <999> but was: <200>";

  @Test
  void theTraceFileOfAnAssertionFailureStatesTheAssertionMessageInHeaderAndBody(
      @TempDir Path outputDir) throws Exception {
    var failed = run(outputDir);

    assertThat(failed).isEqualTo(1);
    var trace =
        Files.readString(
            outputDir.resolve(
                "traces/AssertionFailureFixture/pricing_total_disagrees_with_the_reference_basket.md"));
    assertThat(trace)
        .contains(
            "Failing call: none, no recorded call threw; the test itself failed: " + MESSAGE + ".")
        .contains(
            "**Test failure:** total for the reference basket ==&gt; expected: &lt;999&gt; but was:"
                + " &lt;200&gt;");
  }

  @Test
  void theIndexRowStillCarriesTheSameMessage(@TempDir Path outputDir) throws Exception {
    run(outputDir);

    assertThat(Files.readString(outputDir.resolve("INDEX.md")))
        .contains(
            "| AssertionFailureFixture.pricingTotalDisagreesWithTheReferenceBasket | failed |")
        .contains(" | " + MESSAGE + " |");
  }

  private static long run(Path outputDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    var listener = new SummaryGeneratingListener();
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(DiscoverySelectors.selectClass(AssertionFailureFixture.class))
                  .configurationParameter(AssertionFailureFixture.HARNESS_PARAMETER, "true")
                  .build(),
              listener);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
    return listener.getSummary().getTestsFailedCount();
  }
}
