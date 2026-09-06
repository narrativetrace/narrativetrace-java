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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class NarrativeTraceRuleIntegrationTest {

  @Test
  void failingJunit4TestPrintsFailureReport() {
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4FailingFixture.class))
                  .build());
    } finally {
      System.setOut(oldOut);
    }

    var output = captured.toString();
    assertThat(output).contains("Scenario: Failing test");
    assertThat(output).contains("Service.doWork");
  }

  @Test
  void outputSystemPropertyEnablesFileWriting(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
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

    var traceFile = tempDir.resolve("traces/Junit4FailingFixture/failing_test.md");
    assertThat(traceFile).exists();
    try {
      assertThat(java.nio.file.Files.readString(traceFile)).contains("Service.doWork");
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }
}
