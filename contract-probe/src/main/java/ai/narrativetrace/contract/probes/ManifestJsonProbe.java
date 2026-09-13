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
 * {@code probed-default}: a run writes {@code <outputDir>/manifest.json}, one row per traced
 * scenario (structural-trace-format.md). {@code narrativetrace.output=true} is this probe's own
 * setup — the SEPARATE output-on-by-default claim is what {@link OutputDefaultProbe} tests.
 */
public final class ManifestJsonProbe {

  private ManifestJsonProbe() {}

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-manifest");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.setProperty("narrativetrace.output", "true");
      try {
        JUnitLauncherSupport.run(TracedCallFixture.class);
        return Files.isRegularFile(outputDir.resolve("manifest.json")) ? "true" : "false";
      } finally {
        System.clearProperty("narrativetrace.outputDir");
        System.clearProperty("narrativetrace.output");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
