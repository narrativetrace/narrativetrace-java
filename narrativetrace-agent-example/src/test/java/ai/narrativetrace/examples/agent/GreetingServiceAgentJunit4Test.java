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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * §176 proof, JUnit 4 half: {@link GreetingServiceAgentJunit4Fixture} carries no tracing code
 * beyond the linked {@code NarrativeTraceClassRule}/{@code NarrativeTraceRule} pair, yet produces
 * the full per-test file set for a class the agent wove — no proxy, no annotation. JUnit 4 writes
 * no {@code manifest.json} (that is a JUnit 5 suite-level artifact), so this proof stops at the
 * Markdown/structural/JSON/diagram set.
 *
 * <p>Run via the JUnit Platform's vintage engine, the same way {@link GreetingServiceAgentTest}
 * runs its JUnit 5 fixture — both nested launches run inside this task's {@code
 * -javaagent}-attached JVM, and both wait for their fixture class to finish before reading what it
 * wrote.
 */
@Tag("agent")
class GreetingServiceAgentJunit4Test {

  @Test
  void agentModeWritesThePerTestFilesUnderJUnit4WithNoTracingCodeInTheSubject(@TempDir Path tempDir)
      throws IOException {
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runNested(GreetingServiceAgentJunit4Fixture.class);
    } finally {
      System.clearProperty("narrativetrace.outputDir");
    }

    var traceDir = tempDir.resolve("traces").resolve("GreetingServiceAgentJunit4Fixture");
    assertThat(traceDir).isDirectory();

    var markdown = onlyFileEndingWith(traceDir, ".md");
    assertThat(Files.readString(markdown)).contains("GreetingService.greet");
    assertThat(onlyFileEndingWith(traceDir, ".json")).exists();
    assertThat(
            onlyFileEndingWith(
                tempDir.resolve("structural").resolve("GreetingServiceAgentJunit4Fixture"), ".nt"))
        .exists();
    assertThat(
            onlyFileEndingWith(
                tempDir.resolve("diagrams").resolve("GreetingServiceAgentJunit4Fixture"), ".mmd"))
        .exists();
  }

  private static void runNested(Class<?> fixture) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build();
    LauncherFactory.create().execute(request);
  }

  private static Path onlyFileEndingWith(Path dir, String suffix) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> matches = files.filter(p -> p.toString().endsWith(suffix)).toList();
      assertThat(matches).as("a *%s file under %s", suffix, dir).hasSize(1);
      return matches.get(0);
    }
  }
}
