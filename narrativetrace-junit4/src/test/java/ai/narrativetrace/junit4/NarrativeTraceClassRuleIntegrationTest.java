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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class NarrativeTraceClassRuleIntegrationTest {

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void classRuleProducesFullOutputStructure(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4MultiTestFixture.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    // Trace files
    assertThat(tempDir.resolve("traces/Junit4MultiTestFixture/customer_places_order.md")).exists();
    assertThat(tempDir.resolve("traces/Junit4MultiTestFixture/customer_cancels_order.md")).exists();

    // Clarity report
    assertThat(tempDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void classRulePrintsConsoleSummary(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4MultiTestFixture.class))
                  .build());
    } finally {
      System.setOut(oldOut);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var output = captured.toString();
    assertThat(output).contains("NarrativeTrace — Suite complete");
    assertThat(output).contains("2 scenarios recorded");
    assertThat(output).contains("Clarity:");
  }

  @Test
  void classRuleClarityReportContainsScenarios(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4MultiTestFixture.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    var content = Files.readString(tempDir.resolve("clarity-report.md"));
    assertThat(content).contains("Customer places order");
    assertThat(content).contains("Customer cancels order");
  }
}
