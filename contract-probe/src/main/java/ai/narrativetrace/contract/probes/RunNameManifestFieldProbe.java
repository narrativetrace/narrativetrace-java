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
 * {@code probed-default}, since 0.2.2: {@code manifest.json} carries a top-level {@code run} object
 * naming the run this manifest belongs to (configuration-guide.md § The run has a name). {@code
 * narrativetrace.output=true} is this probe's own setup, matching {@link ManifestJsonProbe}.
 */
public final class RunNameManifestFieldProbe {

  private RunNameManifestFieldProbe() {}

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-run-name-manifest");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.setProperty("narrativetrace.output", "true");
      try {
        JUnitLauncherSupport.run(TracedCallFixture.class);
        var manifest = Files.readString(outputDir.resolve("manifest.json"));
        return manifest.contains("\"run\": {") && manifest.contains("\"name\": \"")
            ? "true"
            : "false";
      } finally {
        System.clearProperty("narrativetrace.outputDir");
        System.clearProperty("narrativetrace.output");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
