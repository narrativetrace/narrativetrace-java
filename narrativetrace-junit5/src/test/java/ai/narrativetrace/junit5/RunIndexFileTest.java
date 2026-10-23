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

/**
 * {@code INDEX.md} is the one file the prompt can name: a paragraph on what the directory is, the
 * failing tests first with their trace and error, then the passing ones.
 */
class RunIndexFileTest {

  @Test
  void aRunWithTwoFailingAndThreePassingTestsWritesAFailingFirstIndex(@TempDir Path outputDir)
      throws Exception {
    FailurePointerTest.run(outputDir);

    var index = Files.readString(outputDir.resolve("INDEX.md"));
    assertThat(index).startsWith("# NarrativeTrace run index\n\nThis directory holds");
    var failing = index.indexOf("## Failing");
    var passing = index.indexOf("## Passing");
    assertThat(failing).isPositive().isLessThan(passing);
    var failingSection = index.substring(failing, passing);
    assertThat(failingSection)
        .contains(
            "| MixedOutcomeFailingTestFixture.bFailsOnStock | failed |"
                + " `traces/MixedOutcomeFailingTestFixture/b_fails_on_stock.md` |")
        .contains(
            "| MixedOutcomeFailingTestFixture.dFailsOnPayment | failed |"
                + " `traces/MixedOutcomeFailingTestFixture/d_fails_on_payment.md` |")
        .doesNotContain("PassesFirst");
    assertThat(failingSection.indexOf("bFailsOnStock"))
        .isLessThan(failingSection.indexOf("dFailsOnPayment"));
    assertThat(index.substring(passing))
        .contains("MixedOutcomeFailingTestFixture.aPassesFirst | passed |")
        .contains("MixedOutcomeFailingTestFixture.cPassesSecond | passed |")
        .contains("MixedOutcomeFailingTestFixture.ePassesThird | passed |");
  }

  @Test
  void theErrorColumnIsTheFilteredMessageOnOneLineWithPipesEscaped(@TempDir Path outputDir)
      throws Exception {
    FailurePointerTest.run(outputDir);

    var index = Files.readString(outputDir.resolve("INDEX.md"));
    var row =
        index.lines().filter(line -> line.contains("bFailsOnStock")).findFirst().orElseThrow();
    assertThat(row).endsWith("| stock check \\| failed\\nsecond line |");
    assertThat(index.lines().filter(line -> line.contains("bFailsOnStock"))).hasSize(1);
  }

  @Test
  void theManifestRowsCarryTheOutcomeAndTheMessage(@TempDir Path outputDir) throws Exception {
    FailurePointerTest.run(outputDir);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    assertThat(manifest).contains("\"schema\": \"narrativetrace/scenario-manifest/2\"");
    assertThat(manifest).contains("\"outcome\": \"failed\"").contains("\"outcome\": \"passed\"");
    assertThat(manifest).contains("\"message\": \"stock check | failed\\\\nsecond line\"");
  }

  @Test
  void theIndexIsMovedIntoPlaceNeverLeftHalfWritten(@TempDir Path outputDir) throws Exception {
    FailurePointerTest.run(outputDir);

    try (var files = Files.list(outputDir)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .contains("INDEX.md")
          .noneMatch(name -> name.endsWith(".tmp"));
    }
  }
}
