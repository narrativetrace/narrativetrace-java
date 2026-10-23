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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What one TestNG run writes per test method, and the properties that steer it. */
class TraceOutputTest {

  @Test
  void oneTraceFilePerTestMethod(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "OrderFixture", "customer_places_order"))
        .contains("OrderService.placeOrder")
        .doesNotContain("cancelOrder");
    assertThat(TestNgRuns.trace(outputDir, "OrderFixture", "customer_cancels_order"))
        .contains("OrderService.cancelOrder")
        .doesNotContain("placeOrder");
  }

  @Test
  void aTestThatTracedNothingWritesNoFile(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(TestNgRuns.listing(outputDir)).noneMatch(file -> file.contains("quiet_test"));
  }

  @Test
  void theFullOutputStructureIsWritten(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(TestNgRuns.listing(outputDir))
        .contains(
            "traces/OrderFixture/customer_places_order.md",
            "traces/OrderFixture/customer_places_order.json",
            "manifest.json",
            "INDEX.md",
            "clarity-report.md",
            "clarity-results.json");
    assertThat(TestNgRuns.listing(outputDir))
        .anyMatch(file -> file.startsWith("diagrams/") && file.contains("customer_places_order"));
  }

  @Test
  void theTraceFileOpensWithTheHeader(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "OrderFixture", "customer_places_order"))
        .contains("NarrativeTrace trace file")
        .contains(
            "What this is: the recorded call tree of one test run: "
                + "OrderFixture.customerPlacesOrder, passed");
  }

  @Test
  void theNarrativeCarriesTheHumanizedScenario(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "OrderFixture", "customer_places_order"))
        .contains("Customer places order");
  }

  @Test
  void outputIsOnWithNoOutputProperty(@TempDir Path outputDir) throws Exception {
    System.clearProperty("narrativetrace.output");

    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.md")).exists();
  }

  @Test
  void outputFalseWritesNothing(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(
        outputDir, Map.of("narrativetrace.output", "false"), testng -> {}, OrderFixture.class);

    assertThat(TestNgRuns.listing(outputDir)).isEmpty();
  }

  @Test
  void theOutputDirPropertyPlacesEveryArtifact(@TempDir Path root) throws Exception {
    var outputDir = root.resolve("nested/out");

    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.md")).exists();
    assertThat(outputDir.resolve("manifest.json")).exists();
    try (var siblings = Files.list(root)) {
      assertThat(siblings.map(p -> p.getFileName().toString())).containsExactly("nested");
    }
  }

  @Test
  void theFormatPropertySelectsTheRenderer(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(
        outputDir, Map.of("narrativetrace.format", "text"), testng -> {}, OrderFixture.class);

    assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.txt")).exists();
    assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.md")).doesNotExist();
  }

  @Test
  void canonicalAndStructuralJsonAreOptIn(@TempDir Path root) throws Exception {
    var plain = root.resolve("plain");
    var both = root.resolve("both");

    TestNgRuns.run(plain, OrderFixture.class);
    TestNgRuns.run(
        both,
        Map.of("narrativetrace.canonicalJson", "true", "narrativetrace.structuralJson", "true"),
        testng -> {},
        OrderFixture.class);

    assertThat(TestNgRuns.listing(plain))
        .noneMatch(file -> file.endsWith(".canonical.json") || file.endsWith(".structural.json"));
    assertThat(TestNgRuns.listing(both))
        .contains(
            "traces/OrderFixture/customer_places_order.canonical.json",
            "traces/OrderFixture/customer_places_order.structural.json");
  }

  @Test
  void unresolvedTemplatePlaceholdersAreReported(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, TemplateFixture.class);

    assertThat(run.out())
        .contains("Unresolved template placeholder")
        .contains("OrderService.placeOrder")
        .contains("{custmerId}");
  }

  @Test
  void anUnwritableOutputDirReportsAndDoesNotFailTheRun(@TempDir Path outputDir) throws Exception {
    Files.writeString(outputDir.resolve("traces"), "a file where a directory belongs");

    var run = TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(run.err()).contains("Failed to write trace file:");
    assertThat(run.failed()).isFalse();
  }
}
