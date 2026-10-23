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

/** The run's clarity report: written once at run end, scored in the committed vocabulary. */
class ClarityReportTest {

  private static final String TRADING_GLOSSARY =
      """
      {
        "schemaVersion": 1,
        "contexts": {"trading": {"packages": ["com.acme.trading"]}},
        "terms": [
          {
            "term": "fold tranche",
            "context": "trading",
            "kind": "verb-phrase",
            "status": "curated",
            "firstSeen": "2020-01-01"
          }
        ]
      }
      """;

  @Test
  void theRunWritesTheClarityReportAndJson(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(Files.readString(outputDir.resolve("clarity-report.md")))
        .contains("Customer places order")
        .contains("Customer cancels order");
    assertThat(Files.readString(outputDir.resolve("clarity-results.json")))
        .contains("Customer places order");
  }

  @Test
  void duplicateScenarioNamesFromTwoClassesAreBothKept(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, WarehouseFixture.class, DuplicateWarehouseFixture.class);

    var json = Files.readString(outputDir.resolve("clarity-results.json"));
    assertThat(json.split("Warehouse packs parcel", -1)).hasSize(3);
  }

  @Test
  void theReportScoresInTheCommittedVocabulary(@TempDir Path root) throws Exception {
    var withGlossary = root.resolve("with");
    var without = root.resolve("without");
    var glossaryDir = Files.createDirectories(root.resolve("glossary"));
    Files.writeString(glossaryDir.resolve("glossary.json"), TRADING_GLOSSARY);

    TestNgRuns.run(
        withGlossary,
        Map.of("narrativetrace.glossaryDir", glossaryDir.toString()),
        testng -> {},
        TrancheFixture.class);
    TestNgRuns.run(
        without,
        Map.of("narrativetrace.glossaryDir", root.resolve("empty").toString()),
        testng -> {},
        TrancheFixture.class);

    assertThat(Files.readString(withGlossary.resolve("clarity-report.md")))
        .contains("Domain verb 'fold' + domain noun 'tranche'");
    assertThat(Files.readString(without.resolve("clarity-report.md")))
        .doesNotContain("Domain verb 'fold'");
  }

  @Test
  void aMalformedGlossaryDegradesToTheBuiltInDictionaries(
      @TempDir Path outputDir, @TempDir Path glossaryDir) throws Exception {
    Files.writeString(glossaryDir.resolve("glossary.json"), "{ this is not json");

    var run =
        TestNgRuns.run(
            outputDir,
            Map.of("narrativetrace.glossaryDir", glossaryDir.toString()),
            testng -> {},
            OrderFixture.class);

    assertThat(run.err()).contains("could not be read, scoring with the built-in dictionaries");
    assertThat(outputDir.resolve("clarity-report.md")).exists();
    assertThat(run.failed()).isFalse();
  }

  @Test
  void anUnwritableClarityReportIsReportedAndDoesNotFailTheRun(@TempDir Path outputDir)
      throws Exception {
    Files.createDirectories(outputDir.resolve("clarity-report.md"));

    var run = TestNgRuns.run(outputDir, OrderFixture.class);

    assertThat(run.err()).contains("Failed to write clarity report:");
    assertThat(run.failed()).isFalse();
  }
}
