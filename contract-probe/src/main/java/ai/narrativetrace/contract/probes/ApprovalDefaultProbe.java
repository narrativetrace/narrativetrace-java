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
import java.util.stream.Stream;

/**
 * {@code probed-default}: {@code narrativetrace.approval} defaults to {@code false} — a passing
 * test with no committed baseline must write no approval bookkeeping artifact at all (never {@code
 * .received.nt}, never an auto-written {@code .approved.nt}). {@code narrativetrace.output} is
 * forced {@code true} for this probe's own setup only, so it observes approval specifically rather
 * than the separately-tested output-on-by-default claim.
 */
public final class ApprovalDefaultProbe {

  private ApprovalDefaultProbe() {}

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-approval");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.setProperty("narrativetrace.output", "true");
      System.clearProperty("narrativetrace.approval"); // the default under test — left unset
      try {
        JUnitLauncherSupport.run(TracedCallFixture.class);
        try (Stream<Path> walk = Files.walk(outputDir)) {
          boolean approvalArtifact =
              walk.anyMatch(
                  p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".received.nt")
                        || name.endsWith(".approved.nt")
                        || name.endsWith(".incomplete.nt");
                  });
          return approvalArtifact ? "true" : "false";
        }
      } finally {
        System.clearProperty("narrativetrace.outputDir");
        System.clearProperty("narrativetrace.output");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
