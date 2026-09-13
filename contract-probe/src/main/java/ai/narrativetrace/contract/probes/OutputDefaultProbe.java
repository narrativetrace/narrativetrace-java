/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.contract.fixtures.TracedCallFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code probed-default}, since 0.2.2 — not exercised against 0.2.1, where the documented default
 * was still {@code false} (the default flipped after the {@code v0.2.1} tag). {@code
 * narrativetrace.output} is deliberately left unset here: that absence IS the claim under test.
 */
public final class OutputDefaultProbe {

  private OutputDefaultProbe() {}

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-output-default");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.clearProperty("narrativetrace.output");
      try {
        JUnitLauncherSupport.run(TracedCallFixture.class);
        return Files.isRegularFile(outputDir.resolve("manifest.json")) ? "true" : "false";
      } finally {
        System.clearProperty("narrativetrace.outputDir");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
