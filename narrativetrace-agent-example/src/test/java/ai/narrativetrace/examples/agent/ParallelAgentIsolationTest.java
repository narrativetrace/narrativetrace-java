/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * §176 build step 3 proof: {@link ParallelGreetingAgentFixture}'s two tests run concurrently, on
 * two different threads, against the one context the agent is routed into — and each one's file
 * carries only its own call. No thread-routed context is needed: {@code
 * ThreadLocalNarrativeContext}'s stack is already thread-local, so a shared context object sharing
 * one pipeline isolates concurrent tests the same way it always isolated concurrent requests in a
 * long-lived production context.
 */
@Tag("agent")
class ParallelAgentIsolationTest {

  @Test
  void twoAgentInstrumentedTestsInParallelEachFileHoldsOnlyItsOwnCall(@TempDir Path tempDir)
      throws IOException {
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runNestedInParallel(ParallelGreetingAgentFixture.class);
    } finally {
      System.clearProperty("narrativetrace.outputDir");
    }

    var traceDir = tempDir.resolve("traces").resolve("ParallelGreetingAgentFixture");
    var aliceFile = onlyFileMatching(traceDir, "alice");
    var bobFile = onlyFileMatching(traceDir, "bob");

    assertThat(Files.readString(aliceFile)).contains("Alice-only").doesNotContain("Bob-only");
    assertThat(Files.readString(bobFile)).contains("Bob-only").doesNotContain("Alice-only");
  }

  private static void runNestedInParallel(Class<?> fixture) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
            .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
            .build();
    LauncherFactory.create().execute(request);
  }

  private static Path onlyFileMatching(Path dir, String needle) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.getFileName().toString().toLowerCase().contains(needle))
          .filter(p -> p.toString().endsWith(".md"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("no .md file matching '" + needle + "' in " + dir));
    }
  }
}
