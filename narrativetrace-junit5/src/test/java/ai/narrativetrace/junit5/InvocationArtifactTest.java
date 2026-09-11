/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Per-invocation artifact identity: a parameterized test method runs more than once, and every
 * invocation is entitled to its own artifacts and to a manifest entry that reaches them.
 */
class InvocationArtifactTest {

  @Test
  void eachParameterizedInvocationOwnsItsOwnTraceArtifacts(@TempDir Path outputDir)
      throws IOException {
    runSuite(outputDir, ParameterizedFixture.class);

    assertThat(fileNamesUnder(outputDir.resolve("traces/ParameterizedFixture"), ".md"))
        .containsExactly(
            "equipment_can_be_found-001-find_kayak.md", "equipment_can_be_found-002-find_tent.md");
    assertThat(
            Files.readString(
                outputDir.resolve(
                    "traces/ParameterizedFixture/equipment_can_be_found-001-find_kayak.md")))
        .contains("\"KAYAK\"")
        .doesNotContain("\"TENT\"");
    assertThat(
            Files.readString(
                outputDir.resolve(
                    "traces/ParameterizedFixture/equipment_can_be_found-002-find_tent.md")))
        .contains("\"TENT\"")
        .doesNotContain("\"KAYAK\"");
  }

  @Test
  void everyInvocationGetsItsOwnStructuralAndDiagramArtifacts(@TempDir Path outputDir)
      throws IOException {
    runSuite(outputDir, ParameterizedFixture.class);

    assertThat(fileNamesUnder(outputDir.resolve("structural/ParameterizedFixture"), ".nt"))
        .containsExactly(
            "equipment_can_be_found-001-find_kayak.nt", "equipment_can_be_found-002-find_tent.nt");
    assertThat(fileNamesUnder(outputDir.resolve("diagrams/ParameterizedFixture"), ".mmd"))
        .containsExactly(
            "equipment_can_be_found-001-find_kayak.mmd",
            "equipment_can_be_found-002-find_tent.mmd");
  }

  @Test
  void theManifestReachesEveryInvocationsFiles(@TempDir Path outputDir) throws IOException {
    runSuite(outputDir, ParameterizedFixture.class);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    assertThat(manifest).contains("\"scenario\": \"find KAYAK\"");
    assertThat(manifest).contains("\"scenario\": \"find TENT\"");
    assertThat(manifest).contains("\"invocation\": 1");
    assertThat(manifest).contains("\"invocation\": 2");
    assertThat(manifest)
        .contains("traces/ParameterizedFixture/equipment_can_be_found-001-find_kayak.md");
    assertThat(manifest)
        .contains("structural/ParameterizedFixture/equipment_can_be_found-002-find_tent.nt");
  }

  @Test
  void anOrdinaryTestMethodKeepsItsUndecoratedArtifactName(@TempDir Path outputDir)
      throws IOException {
    runSuite(outputDir, MultiTestFixture.class);

    assertThat(fileNamesUnder(outputDir.resolve("traces/MultiTestFixture"), ".md"))
        .containsExactly("customer_cancels_order.md", "customer_places_order.md");
  }

  @Test
  void unfoldedModeKeepsEveryIterationsValuesInTheNarrative(@TempDir Path outputDir)
      throws IOException {
    System.setProperty("narrativetrace.unfolded", "true");
    try {
      runSuite(outputDir, LoopFixture.class);
    } finally {
      System.clearProperty("narrativetrace.unfolded");
    }

    var narrative =
        Files.readString(outputDir.resolve("traces/LoopFixture/catalog_is_read_twice.md"));
    assertThat(narrative).contains("\"KAYAK\"").contains("\"TENT\"");
    assertThat(narrative).doesNotContain(" more");
  }

  @Test
  void foldingStaysTheDefaultAndTheFoldLineNamesTheFoldedIteration(@TempDir Path outputDir)
      throws IOException {
    runSuite(outputDir, LoopFixture.class);

    var narrative =
        Files.readString(outputDir.resolve("traces/LoopFixture/catalog_is_read_twice.md"));
    assertThat(narrative).contains("×1 more: #2 sku=`\"TENT\"`");
  }

  private static List<String> fileNamesUnder(Path directory, String suffix) throws IOException {
    try (var files = Files.list(directory)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(suffix))
          .sorted()
          .toList();
    }
  }

  private static void runSuite(Path outputDir, Class<?>... fixtures) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    try {
      var request =
          LauncherDiscoveryRequestBuilder.request()
              .selectors(
                  Arrays.stream(fixtures)
                      .map(DiscoverySelectors::selectClass)
                      .toArray(DiscoverySelector[]::new));
      LauncherFactory.create().execute(request.build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }
}
