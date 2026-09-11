/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioManifestTest {

  @Test
  void listsOnlyTheArtifactsThatExist(@TempDir Path outputDir) throws IOException {
    var identity = ArtifactIdentity.ofInvocation("com.example.CatalogTest", "finds", 2, "TENT");
    write(outputDir.resolve("traces/CatalogTest/finds-002-tent.md"));
    write(outputDir.resolve("structural/CatalogTest/finds-002-tent.nt"));

    var entry = ScenarioManifest.entryFor(outputDir, identity, "find TENT");

    assertThat(entry.artifacts())
        .containsExactly(
            Map.entry("trace", "traces/CatalogTest/finds-002-tent.md"),
            Map.entry("structural", "structural/CatalogTest/finds-002-tent.nt"));
  }

  @Test
  void namesEveryArtifactRoleOfAFullMarkdownRun(@TempDir Path outputDir) throws IOException {
    var identity = ArtifactIdentity.ofMethod("com.example.CatalogTest", "finds");
    write(outputDir.resolve("traces/CatalogTest/finds.md"));
    write(outputDir.resolve("traces/CatalogTest/finds.json"));
    write(outputDir.resolve("traces/CatalogTest/finds.canonical.json"));
    write(outputDir.resolve("traces/CatalogTest/finds.structural.json"));
    write(outputDir.resolve("diagrams/CatalogTest/finds.mmd"));
    write(outputDir.resolve("structural/CatalogTest/finds.nt"));

    var entry = ScenarioManifest.entryFor(outputDir, identity, "finds");

    assertThat(entry.artifacts().keySet())
        .containsExactly(
            "trace", "json", "canonicalJson", "structuralJson", "diagram", "structural");
  }

  @Test
  void aTextFormatRunListsItsOwnTraceFile(@TempDir Path outputDir) throws IOException {
    var identity = ArtifactIdentity.ofMethod("CatalogTest", "finds");
    write(outputDir.resolve("traces/CatalogTest/finds.txt"));

    var entry = ScenarioManifest.entryFor(outputDir, identity, "finds");

    assertThat(entry.artifacts())
        .containsExactly(Map.entry("trace", "traces/CatalogTest/finds" + ".txt"));
  }

  @Test
  void aScenarioWithNoArtifactsOnDiskStillGetsARow(@TempDir Path outputDir) {
    var entry =
        ScenarioManifest.entryFor(outputDir, ArtifactIdentity.ofMethod("T", "finds"), "finds");

    assertThat(entry.artifacts()).isEmpty();
    assertThat(ScenarioManifest.render(List.of(entry))).contains("\"artifacts\": {\n      }");
  }

  @Test
  void rendersInvocationRowsWithTheirIndexAndOrdinaryRowsWithout() {
    var manifest =
        ScenarioManifest.render(
            List.of(
                new ScenarioManifest.Entry(
                    "customer places order",
                    ArtifactIdentity.ofMethod("com.example.OrderTest", "customerPlacesOrder"),
                    Map.of("trace", "traces/OrderTest/customer_places_order.md")),
                new ScenarioManifest.Entry(
                    "find TENT",
                    ArtifactIdentity.ofInvocation("com.example.CatalogTest", "finds", 2, "TENT"),
                    Map.of("trace", "traces/CatalogTest/finds-002-tent.md"))));

    assertThat(manifest).contains("\"schema\": \"narrativetrace/scenario-manifest/1\"");
    assertThat(manifest).contains("\"scenario\": \"customer places order\"");
    assertThat(manifest).contains("\"testClass\": \"com.example.OrderTest\"");
    assertThat(manifest).contains("\"testMethod\": \"customerPlacesOrder\"");
    assertThat(manifest).contains("\"invocation\": 2");
    assertThat(manifest.indexOf("\"invocation\""))
        .isGreaterThan(manifest.indexOf("customer places order"));
    assertThat(manifest.lines().filter(line -> line.contains("\"invocation\"")).count()).isOne();
  }

  @Test
  void escapesWhateverADisplayNameCarries() {
    var manifest =
        ScenarioManifest.render(
            List.of(
                new ScenarioManifest.Entry(
                    "a \"quoted\"\nname",
                    ArtifactIdentity.ofMethod("T", "runs"),
                    Map.of("trace", "traces/T/runs.md"))));

    assertThat(manifest).contains("\"scenario\": \"a \\\"quoted\\\"\\nname\"");
  }

  @Test
  void rowsRenderInTheOrderTheRunRecordedThem() {
    var artifacts = new LinkedHashMap<String, String>();
    artifacts.put("trace", "traces/T/b.md");
    artifacts.put("structural", "structural/T/b.nt");
    var entry = new ScenarioManifest.Entry("b", ArtifactIdentity.ofMethod("T", "b"), artifacts);

    var manifest = ScenarioManifest.render(List.of(entry));

    assertThat(manifest.indexOf("\"trace\"")).isLessThan(manifest.indexOf("\"structural\""));
    assertThat(entry.artifacts()).containsExactlyEntriesOf(artifacts);
  }

  @Test
  void writesTheManifestBesideTheArtifacts(@TempDir Path outputDir) throws IOException {
    var entry =
        new ScenarioManifest.Entry(
            "finds", ArtifactIdentity.ofMethod("T", "finds"), Map.of("trace", "traces/T/finds.md"));

    ScenarioManifest.write(List.of(entry), outputDir);

    assertThat(Files.readString(outputDir.resolve("manifest.json")))
        .isEqualTo(ScenarioManifest.render(List.of(entry)));
  }

  @Test
  void writesNothingWhenTheRunTracedNothing(@TempDir Path outputDir) throws IOException {
    ScenarioManifest.write(List.of(), outputDir);

    assertThat(outputDir.resolve("manifest.json")).doesNotExist();
  }

  private static void write(Path file) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, "x");
  }
}
