/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * {@link NarrativeTraceExtension} is published as an {@code Extension} via {@code
 * META-INF/services} (see {@code src/main/resources}), so JUnit's own {@code
 * junit.jupiter.extensions.autodetection.enabled} configuration parameter registers it suite-wide
 * without any {@code @ExtendWith} on the test class. {@link AutodetectionFixture} deliberately
 * carries no such annotation: this is the second, tested registration path (alongside the per-class
 * {@code @ExtendWith} every other fixture in this module uses).
 */
class ExtensionAutodetectionTest {

  @Test
  void withoutAutodetectionAnUnannotatedFixtureFailsParameterResolution(@TempDir Path outputDir) {
    TestExecutionSummary summary =
        runSuiteWithSummary(Map.of("narrativetrace.outputDir", outputDir.toString()));

    assertThat(summary.getTestsFailedCount()).isOne();
    assertThat(outputDir.resolve("clarity-report.md")).doesNotExist();
  }

  @Test
  void autodetectionEnabledResolvesTheContextAndWritesTheAggregateReport(@TempDir Path outputDir) {
    TestExecutionSummary summary =
        runSuiteWithSummary(
            Map.of(
                "junit.jupiter.extensions.autodetection.enabled",
                "true",
                "narrativetrace.outputDir",
                outputDir.toString()));

    assertThat(summary.getTestsFailedCount()).isZero();
    assertThat(summary.getTestsSucceededCount()).isOne();
    assertThat(outputDir.resolve("traces/AutodetectionFixture/warehouse_restocks_shelf.md"))
        .exists();
    assertThat(outputDir.resolve("clarity-results.json")).exists();
    assertThat(outputDir.resolve("clarity-report.md")).exists();
  }

  private static TestExecutionSummary runSuiteWithSummary(
      Map<String, String> configurationParameters) {
    var requestBuilder =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(AutodetectionFixture.class));
    configurationParameters.forEach(requestBuilder::configurationParameter);
    var listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(requestBuilder.build(), listener);
    return listener.getSummary();
  }
}
