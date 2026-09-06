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

/**
 * Verifies the claim {@link NarrativeTestCase}'s JavaDoc makes about its inherited static {@link
 * NarrativeTraceClassRule}: two unrelated subclasses running in the same JVM share one instance
 * (declared once, in the superclass), and that sharing is safe because {@code afterAll()} fully
 * drains and clears it between classes.
 *
 * <p>{@link NarrativeTestCaseFixtureOne} (two test methods) and {@link NarrativeTestCaseFixtureTwo}
 * (one test method) both extend {@link NarrativeTestCase}; this test runs both under the JUnit 5
 * launcher, the way {@link NarrativeTraceClassRuleIntegrationTest} runs standalone two-rule
 * fixtures.
 */
class NarrativeTestCaseInheritanceTest {

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void eachSubclassGetsFreshPerTestContextsWithNoBleedBetweenThem(@TempDir Path tempDir)
      throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(
                      selectClass(NarrativeTestCaseFixtureOne.class),
                      selectClass(NarrativeTestCaseFixtureTwo.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    assertFixtureOneTracesAreIsolated(tempDir);
    assertFixtureTwoTraceIsIsolated(tempDir);
  }

  /**
   * Each of fixture one's two test methods gets its own trace file, each containing only its own
   * call and neither the other test method's call nor fixture two's — proof of fresh per-test
   * contexts and no cross-subclass bleed.
   */
  private void assertFixtureOneTracesAreIsolated(Path tempDir) throws Exception {
    var placesOrder =
        Files.readString(
            tempDir.resolve("traces/NarrativeTestCaseFixtureOne/customer_places_order.md"));
    assertThat(placesOrder).contains("OrderService.placeOrder");
    assertThat(placesOrder).doesNotContain("OrderService.cancelOrder");
    assertThat(placesOrder).doesNotContain("InventoryService.checkStock");

    var cancelsOrder =
        Files.readString(
            tempDir.resolve("traces/NarrativeTestCaseFixtureOne/customer_cancels_order.md"));
    assertThat(cancelsOrder).contains("OrderService.cancelOrder");
    assertThat(cancelsOrder).doesNotContain("OrderService.placeOrder");
    assertThat(cancelsOrder).doesNotContain("InventoryService.checkStock");
  }

  private void assertFixtureTwoTraceIsIsolated(Path tempDir) throws Exception {
    var checksInventory =
        Files.readString(
            tempDir.resolve("traces/NarrativeTestCaseFixtureTwo/customer_checks_inventory.md"));
    assertThat(checksInventory).contains("InventoryService.checkStock");
    assertThat(checksInventory).doesNotContain("OrderService.placeOrder");
    assertThat(checksInventory).doesNotContain("OrderService.cancelOrder");
  }

  @Test
  void classEndArtifactsFirePerClassAcrossTheSharedInstance(@TempDir Path tempDir)
      throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    var oldOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(
                      selectClass(NarrativeTestCaseFixtureOne.class),
                      selectClass(NarrativeTestCaseFixtureTwo.class))
                  .build());
    } finally {
      System.setOut(oldOut);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    // Both classes' afterAll() ran and contributed to the one combined report — the shared
    // instance was drained and cleared after fixture one so fixture two's run started clean.
    var report = Files.readString(tempDir.resolve("clarity-report.md"));
    assertThat(report)
        .contains("Customer places order", "Customer cancels order", "Customer checks inventory");

    // The JSON export is one flat entry per scenario, unlike the Markdown report (which
    // names every scenario twice: once in the summary table, once as a section heading) —
    // the format that can actually prove "no duplication, no loss" from the shared instance.
    var json = Files.readString(tempDir.resolve("clarity-results.json"));
    assertThat(countOccurrences(json, "\"name\":\"Customer places order\"")).isEqualTo(1);
    assertThat(countOccurrences(json, "\"name\":\"Customer cancels order\"")).isEqualTo(1);
    assertThat(countOccurrences(json, "\"name\":\"Customer checks inventory\"")).isEqualTo(1);

    assertThat(captured.toString()).contains("3 scenarios recorded");
  }

  private static int countOccurrences(String haystack, String needle) {
    var count = 0;
    var fromIndex = 0;
    while (true) {
      var index = haystack.indexOf(needle, fromIndex);
      if (index < 0) {
        return count;
      }
      count++;
      fromIndex = index + needle.length();
    }
  }
}
