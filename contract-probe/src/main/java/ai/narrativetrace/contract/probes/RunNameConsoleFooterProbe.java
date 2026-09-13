/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.contract.fixtures.TracedCallFixture;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * {@code probed-default}, since 0.2.2: the console suite footer names the enclosing test-suite run
 * (configuration-guide.md § The run has a name) — {@code run: <phrase>} on its own line.
 */
public final class RunNameConsoleFooterProbe {

  private static final Pattern RUN_LINE = Pattern.compile("^\\s*run: [a-z]+ [a-z]+ [a-z]+$");

  private RunNameConsoleFooterProbe() {}

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-run-name-footer");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.setProperty("narrativetrace.output", "true");
      var captured = new ByteArrayOutputStream();
      var original = System.out;
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      try {
        JUnitLauncherSupport.run(TracedCallFixture.class);
      } finally {
        System.setOut(original);
        System.clearProperty("narrativetrace.outputDir");
        System.clearProperty("narrativetrace.output");
      }
      var console = captured.toString(StandardCharsets.UTF_8);
      return console.lines().anyMatch(RUN_LINE.asMatchPredicate()) ? "true" : "false";
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
