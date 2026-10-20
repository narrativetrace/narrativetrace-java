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
 * §176 proof: a class the agent wove — zero NarrativeTrace code in {@link GreetingService} or in
 * {@link GreetingServiceAgentFixture} beyond {@code @ExtendWith(NarrativeTraceExtension.class)} —
 * now produces the full per-test file set, the same as a proxy-traced test always has.
 *
 * <p><b>@llmNote</b> Runs {@link GreetingServiceAgentFixture} through the JUnit Platform Launcher
 * rather than inline, because the files {@link ai.narrativetrace.junit5.NarrativeTraceExtension}
 * writes land in {@code afterTestExecution}, after the {@code @Test} method body returns — a nested
 * launch is the only way this test can observe them once the fixture's own run completes. Only this
 * class is tagged {@code agent} and excluded from the default {@code test} task; the fixture class
 * is excluded from Gradle's own discovery too (see {@code build.gradle.kts}), since {@link
 * GreetingService} must still be unloaded in this JVM when the agent weaves it.
 */
@Tag("agent")
class GreetingServiceAgentTest {

  @Test
  void agentModeWritesThePerTestFilesWithNoTracingCodeInTheSubject(@TempDir Path tempDir)
      throws IOException {
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      runNested(GreetingServiceAgentFixture.class);
    } finally {
      System.clearProperty("narrativetrace.outputDir");
    }

    var traceDir = tempDir.resolve("traces").resolve("GreetingServiceAgentFixture");
    assertThat(traceDir).isDirectory();

    var markdown = onlyFileEndingWith(traceDir, ".md");
    assertThat(Files.readString(markdown)).contains("GreetingService.greet");
    assertThat(onlyFileEndingWith(traceDir, ".json")).exists();
    assertThat(
            onlyFileEndingWith(
                tempDir.resolve("structural").resolve("GreetingServiceAgentFixture"), ".nt"))
        .exists();
    assertThat(
            onlyFileEndingWith(
                tempDir.resolve("diagrams").resolve("GreetingServiceAgentFixture"), ".mmd"))
        .exists();
    assertThat(tempDir.resolve("manifest.json")).exists();
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
