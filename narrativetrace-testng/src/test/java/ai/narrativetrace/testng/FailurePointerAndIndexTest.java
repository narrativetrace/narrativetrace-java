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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An agent that runs a failing test reads the console, so the console says where the trace is: one
 * line under the failure, the same lines again in the footer, failing tests first, and an index on
 * disk that lists failing tests first too.
 */
class FailurePointerAndIndexTest {

  @Test
  void aFailingTestNamesItsTraceFileOnStandardError(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    var expected =
        outputDir
            .toAbsolutePath()
            .normalize()
            .resolve("traces/MixedOutcomeFailingTestFixture/b_fails_on_stock.md");
    assertThat(run.err().lines().toList())
        .contains(
            "narrativetrace: trace of MixedOutcomeFailingTestFixture.bFailsOnStock → " + expected,
            "narrativetrace: trace of MixedOutcomeFailingTestFixture.dFailsOnPayment → "
                + expected.resolveSibling("d_fails_on_payment.md"));
    assertThat(expected).exists();
    assertThat(run.failed()).isTrue();
  }

  @Test
  void aPassingTestPrintsNoPointer(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    assertThat(run.err()).doesNotContain("aPassesFirst").doesNotContain("cPassesSecond");
    assertThat(run.out())
        .doesNotContain("narrativetrace: trace of MixedOutcomeFailingTestFixture.aPasses");
  }

  @Test
  void theFooterListsFailingTracesFirstThenTheSummary(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    List<String> lines = run.out().lines().toList();
    var first = lines.indexOf(pointer(outputDir, "bFailsOnStock", "b_fails_on_stock"));
    var second = lines.indexOf(pointer(outputDir, "dFailsOnPayment", "d_fails_on_payment"));
    var summary = lines.indexOf("NarrativeTrace — Suite complete");
    assertThat(first).as(run.out()).isNotNegative();
    assertThat(second).isEqualTo(first + 1);
    assertThat(lines.get(second + 1))
        .isEqualTo(
            "narrativetrace: index of this run → "
                + outputDir.toAbsolutePath().normalize().resolve("INDEX.md"));
    assertThat(summary).isGreaterThan(second);
  }

  @Test
  void aFailingTestPrintsItsFailureReport(@TempDir Path outputDir) throws Exception {
    var run = TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    // Every written trace is echoed with its scenario; only a failure adds the report.
    assertThat(run.out().split("Execution trace:", -1)).hasSize(3);
    assertThat(run.out())
        .contains("Scenario: B fails on stock\n\nExecution trace:")
        .contains("Scenario: D fails on payment\n\nExecution trace:");
  }

  @Test
  void theIndexListsFailingTestsFirst(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    var index = Files.readString(outputDir.resolve("INDEX.md"));
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
    assertThat(index.substring(passing))
        .contains("MixedOutcomeFailingTestFixture.aPassesFirst | passed |")
        .contains("MixedOutcomeFailingTestFixture.cPassesSecond | passed |")
        .contains("MixedOutcomeFailingTestFixture.ePassesThird | passed |");
    var row =
        index.lines().filter(line -> line.contains("bFailsOnStock")).findFirst().orElseThrow();
    assertThat(row).endsWith("| stock check \\| failed\\nsecond line |");
  }

  @Test
  void theManifestRowsCarryTheOutcomeAndTheMessage(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, MixedOutcomeFailingTestFixture.class);

    var manifest = Files.readString(outputDir.resolve("manifest.json"));
    assertThat(manifest).contains("\"outcome\": \"failed\"").contains("\"outcome\": \"passed\"");
    assertThat(manifest).contains("\"message\": \"stock check | failed\\\\nsecond line\"");
  }

  @Test
  void aFailingRerunReportsTheDeltaAgainstLastGreen(@TempDir Path outputDir) throws Exception {
    EvolvingFlowFixture.grownAndFailing = false;
    TestNgRuns.run(outputDir, EvolvingFlowFixture.class);

    EvolvingFlowFixture.grownAndFailing = true;
    try {
      var failing = TestNgRuns.run(outputDir, EvolvingFlowFixture.class);

      assertThat(failing.out()).contains("Changed since last green").contains("Ledger.record");
    } finally {
      EvolvingFlowFixture.grownAndFailing = false;
    }
  }

  private static String pointer(Path outputDir, String method, String slug) {
    return "narrativetrace: trace of MixedOutcomeFailingTestFixture."
        + method
        + " → "
        + outputDir
            .toAbsolutePath()
            .normalize()
            .resolve("traces/MixedOutcomeFailingTestFixture/" + slug + ".md");
  }
}
