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

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * A suite that fails still writes its class-level report.
 *
 * <p>INTENT: The 2026-09-01 bug hunt found {@code NarrativeTraceClassRule.apply} calling {@code
 * afterAll()} only after {@code base.evaluate()} returned normally. JUnit 4 signals a failing test
 * by letting that statement throw, so the aggregate report — the thing a triage session opens first
 * — was skipped precisely on the runs that needed it.
 */
class FailingSuiteReportTest {

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void aSuiteWhoseClassStatementThrowsStillWritesItsClassLevelReport(@TempDir Path tempDir) {
    runFixture(tempDir);

    assertThat(tempDir.resolve("clarity-report.md")).exists();
  }

  @Test
  void theOriginalFailureIsStillWhatTheRunnerSees(@TempDir Path tempDir) {
    var summary = runFixture(tempDir);

    assertThat(summary.getFailures())
        .as("the class-level throw and the failing test both survive the report")
        .extracting(failure -> failure.getException().getMessage())
        .contains("deliberate class-level failure", "deliberate test failure");
  }

  @Test
  void aFailingSuiteStillWritesTheTraceOfEveryTestThatRan(@TempDir Path tempDir) {
    runFixture(tempDir);

    var traces = tempDir.resolve("traces/Junit4FailingClassRuleFixture");
    assertThat(traces.resolve("customer_places_order.md")).exists();
    assertThat(traces.resolve("customer_payment_is_declined.md")).exists();
  }

  private static org.junit.platform.launcher.listeners.TestExecutionSummary runFixture(
      Path tempDir) {
    var listener = new SummaryGeneratingListener();
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4FailingClassRuleFixture.class))
                  .build(),
              listener);
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
    return listener.getSummary();
  }
}
