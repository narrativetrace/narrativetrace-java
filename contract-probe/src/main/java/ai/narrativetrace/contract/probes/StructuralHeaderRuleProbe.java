/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code probed-default}, since 0.2.3 — not exercised against 0.2.1. An invocation's {@code
 * scenario:} header must name the method and invocation index, never the {@code name = "…"} display
 * name a {@code @ParameterizedTest} template interpolates arguments into
 * (structural-trace-format.md "Artifact identity").
 */
public final class StructuralHeaderRuleProbe {

  private StructuralHeaderRuleProbe() {}

  @ExtendWith(NarrativeTraceExtension.class)
  public static class Fixture {

    public interface Finder {
      String find(String item);
    }

    @ParameterizedTest(name = "find {0}")
    @ValueSource(strings = {"tent", "stove"})
    void findItem(String item, NarrativeContext context) {
      Finder finder = NarrativeTraceProxy.trace((Finder) i -> "found " + i, Finder.class, context);
      finder.find(item);
    }
  }

  public static String observe() {
    try {
      Path outputDir = Files.createTempDirectory("contract-probe-structural-header");
      System.setProperty("narrativetrace.outputDir", outputDir.toString());
      System.setProperty("narrativetrace.output", "true");
      try {
        JUnitLauncherSupport.run(Fixture.class);
        Path structural = outputDir.resolve("structural");
        if (!Files.isDirectory(structural)) {
          return "no-artifact";
        }
        try (Stream<Path> walk = Files.walk(structural)) {
          List<Path> ntFiles = walk.filter(p -> p.toString().endsWith(".nt")).toList();
          for (Path nt : ntFiles) {
            String header =
                Files.readString(nt)
                    .lines()
                    .filter(l -> l.startsWith("scenario:"))
                    .findFirst()
                    .orElse("");
            if (header.contains("find tent") || header.contains("find stove")) {
              return "display-name";
            }
          }
          return ntFiles.isEmpty() ? "no-artifact" : "method-and-index";
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
