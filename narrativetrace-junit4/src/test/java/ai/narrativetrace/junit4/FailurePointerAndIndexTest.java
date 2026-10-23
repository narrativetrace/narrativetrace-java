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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * The JUnit 4 twin of the JUnit 5 pointer and index tests: one run of a fixture with two failing
 * and three passing tests, read several ways. One run, because the fixture's static class rule
 * binds the console streams the first time it is built.
 */
class FailurePointerAndIndexTest {

  @TempDir static Path outputDir;
  private static String out;
  private static String err;

  @BeforeAll
  static void runTheFixtureOnce() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    var outBytes = new ByteArrayOutputStream();
    var errBytes = new ByteArrayOutputStream();
    var originalOut = System.out;
    var originalErr = System.err;
    System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(selectClass(Junit4MixedOutcomeFailingTestFixture.class))
                  .build());
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
    out = outBytes.toString(StandardCharsets.UTF_8);
    err = errBytes.toString(StandardCharsets.UTF_8);
  }

  private static String pointer(String method, String slug) {
    return "narrativetrace: trace of Junit4MixedOutcomeFailingTestFixture."
        + method
        + " → "
        + outputDir
            .toAbsolutePath()
            .normalize()
            .resolve("traces/Junit4MixedOutcomeFailingTestFixture/" + slug + ".md");
  }

  @Test
  void aFailingTestNamesItsTraceFileOnStandardError() {
    assertThat(err.lines().toList())
        .contains(
            pointer("bFailsOnStock", "b_fails_on_stock"),
            pointer("dFailsOnPayment", "d_fails_on_payment"));
    assertThat(err).doesNotContain("aPassesFirst");
  }

  @Test
  void theClassRuleFooterListsTheFailingTracesFirstThenTheSummary() {
    List<String> lines = out.lines().toList();
    var first = lines.indexOf(pointer("bFailsOnStock", "b_fails_on_stock"));
    assertThat(first).isNotNegative();
    assertThat(lines.get(first + 1)).isEqualTo(pointer("dFailsOnPayment", "d_fails_on_payment"));
    assertThat(lines.indexOf("NarrativeTrace — Suite complete")).isGreaterThan(first + 1);
  }

  @Test
  void theClassRuleWritesAFailingFirstIndexAndTheManifest() throws Exception {
    var index = Files.readString(outputDir.resolve("INDEX.md"));

    var failing = index.indexOf("## Failing");
    var passing = index.indexOf("## Passing");
    assertThat(failing).isPositive().isLessThan(passing);
    assertThat(index.substring(failing, passing))
        .contains("Junit4MixedOutcomeFailingTestFixture.bFailsOnStock | failed |")
        .contains("Junit4MixedOutcomeFailingTestFixture.dFailsOnPayment | failed |")
        .contains("stock check \\| failed\\nsecond line");
    assertThat(index.substring(passing)).contains("aPassesFirst | passed |");
    assertThat(Files.readString(outputDir.resolve("manifest.json")))
        .contains("\"outcome\": \"failed\"");
  }
}
