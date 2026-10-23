/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * An agent that runs a failing test reads the console, so the console says where the trace is: one
 * line under the failure, and the same lines again in the run footer, failing tests first.
 */
class FailurePointerTest {

  private record Console(String out, String err) {}

  @Test
  void aFailingTestNamesItsTraceFileOnStandardErrorByAbsolutePath(@TempDir Path outputDir)
      throws Exception {
    var console = run(outputDir);

    var expected =
        outputDir
            .toAbsolutePath()
            .normalize()
            .resolve("traces/MixedOutcomeFailingTestFixture/b_fails_on_stock.md");
    assertThat(console.err().lines().toList())
        .contains(
            "narrativetrace: trace of MixedOutcomeFailingTestFixture.bFailsOnStock → " + expected,
            "narrativetrace: trace of MixedOutcomeFailingTestFixture.dFailsOnPayment → "
                + expected.resolveSibling("d_fails_on_payment.md"));
    assertThat(expected).exists();
  }

  @Test
  void aPassingTestPrintsNoPointer(@TempDir Path outputDir) throws Exception {
    var console = run(outputDir);

    assertThat(console.err()).doesNotContain("aPassesFirst").doesNotContain("cPassesSecond");
    assertThat(console.out())
        .doesNotContain("narrativetrace: trace of MixedOutcomeFailingTestFixture.aPasses");
  }

  @Test
  void theRunFooterListsTheFailingTracesFirstThenTheSummary(@TempDir Path outputDir)
      throws Exception {
    var console = run(outputDir);

    List<String> lines = console.out().lines().toList();
    var first = lines.indexOf(pointer(outputDir, "bFailsOnStock", "b_fails_on_stock"));
    var second = lines.indexOf(pointer(outputDir, "dFailsOnPayment", "d_fails_on_payment"));
    var summary = lines.indexOf("NarrativeTrace — Suite complete");
    assertThat(first).isNotNegative();
    assertThat(second).isEqualTo(first + 1);
    assertThat(lines.get(second + 1))
        .isEqualTo(
            "narrativetrace: index of this run → "
                + outputDir.toAbsolutePath().normalize().resolve("INDEX.md"));
    assertThat(summary).isGreaterThan(second);
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

  static Console run(Path outputDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    var originalOut = System.out;
    var originalErr = System.err;
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(DiscoverySelectors.selectClass(MixedOutcomeFailingTestFixture.class))
                  .build());
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
    return new Console(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  /** A readable failure message if the fixture's files are not where the tests expect. */
  static String listing(Path outputDir) throws Exception {
    try (var files = Files.walk(outputDir)) {
      return files.map(Path::toString).sorted().toList().toString();
    }
  }
}
