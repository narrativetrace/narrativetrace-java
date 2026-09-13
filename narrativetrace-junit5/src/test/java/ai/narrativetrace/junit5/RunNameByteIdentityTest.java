/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Ruling item 3's proof, executed rather than merely argued: the run name and id never enter the
 * structural {@code .nt} text, the manifest's per-scenario rows, or the delta computation — only
 * the run name differs when the same scenario runs as two different test-suite executions.
 *
 * <p><b>@llmNote</b> Each {@link LauncherFactory#create()} call is a fresh JUnit Platform launcher
 * with its own root {@code ExtensionContext} store, so {@link NarrativeTraceExtension}'s {@code
 * GlobalTraceAccumulator} — and the {@link ai.narrativetrace.core.output.RunIdentity} it generates
 * in its constructor — is a fresh instance each time. Two runs of the identical fixture therefore
 * get two different run identities without any test-only reset hook.
 */
class RunNameByteIdentityTest {

  private static final String STRUCTURAL = "structural/ApprovalDriftFixture/booking_is_stored.nt";
  private static final String NARRATIVE = "traces/ApprovalDriftFixture/booking_is_stored.md";

  @Test
  void twoRunsOfTheSameScenarioProduceByteIdenticalStructuralArtifacts(
      @TempDir Path outputOne, @TempDir Path outputTwo) throws Exception {
    var consoleOne = runFixture(outputOne);
    var consoleTwo = runFixture(outputTwo);

    var structuralOne = Files.readString(outputOne.resolve(STRUCTURAL));
    var structuralTwo = Files.readString(outputTwo.resolve(STRUCTURAL));
    assertThat(structuralOne).isEqualTo(structuralTwo);

    // Both runs recorded a fresh scenario in an empty output dir — the delta is "1 new" either
    // way — identical, even though the two runs are different executions.
    assertThat(deltaLine(consoleOne)).isEqualTo(deltaLine(consoleTwo));
  }

  @Test
  void twoRunsNameDifferentRunsInTheFooterAndManifestWhileScenarioRowsMatch(
      @TempDir Path outputOne, @TempDir Path outputTwo) throws Exception {
    var consoleOne = runFixture(outputOne);
    var consoleTwo = runFixture(outputTwo);

    assertThat(runLine(consoleOne)).isNotEqualTo(runLine(consoleTwo));

    var manifestOne = Files.readString(outputOne.resolve("manifest.json"));
    var manifestTwo = Files.readString(outputTwo.resolve("manifest.json"));
    var scenariosOne = manifestOne.substring(manifestOne.indexOf("\"scenarios\""));
    var scenariosTwo = manifestTwo.substring(manifestTwo.indexOf("\"scenarios\""));
    assertThat(scenariosOne).isEqualTo(scenariosTwo);
    assertThat(manifestOne).isNotEqualTo(manifestTwo);
  }

  /**
   * Only the frontmatter's own {@code run:} field is under test here: {@code duration_ms}, {@code
   * trace_id}, {@code trace_name} and the body's {@code ## Trace:}/{@code **Duration:**} lines are
   * real wall-clock timing or the TRACE's own (unrelated, randomly generated) identity — both
   * legitimately differ between two genuinely separate executions, and asserting past them would
   * make this an eventual-consistency test on the clock (rule 3, release-lessons-2026-09).
   */
  @Test
  void theMarkdownFrontmatterDiffersOnlyInItsRunLine(
      @TempDir Path outputOne, @TempDir Path outputTwo) throws Exception {
    runFixture(outputOne);
    runFixture(outputTwo);

    var frontmatterOne = frontmatterFields(outputOne);
    var frontmatterTwo = frontmatterFields(outputTwo);

    assertThat(frontmatterOne).containsKey("run");
    assertThat(frontmatterTwo).containsKey("run");
    assertThat(frontmatterOne.get("run")).isNotEqualTo(frontmatterTwo.get("run"));
    assertThat(frontmatterOne.get("type")).isEqualTo(frontmatterTwo.get("type"));
    assertThat(frontmatterOne.get("scenario")).isEqualTo(frontmatterTwo.get("scenario"));
    assertThat(frontmatterOne.get("entry_point")).isEqualTo(frontmatterTwo.get("entry_point"));
    assertThat(frontmatterOne.get("method_count")).isEqualTo(frontmatterTwo.get("method_count"));
    assertThat(frontmatterOne.get("error_count")).isEqualTo(frontmatterTwo.get("error_count"));
  }

  /** The {@code key: value} lines between the document's opening and closing {@code ---}. */
  private static java.util.Map<String, String> frontmatterFields(Path outputDir)
      throws java.io.IOException {
    var lines = Files.readString(outputDir.resolve(NARRATIVE)).lines().toList();
    var fields = new java.util.LinkedHashMap<String, String>();
    for (var i = 1; i < lines.size() && !lines.get(i).equals("---"); i++) {
      var line = lines.get(i);
      var colon = line.indexOf(": ");
      if (colon > 0) {
        fields.put(line.substring(0, colon), line.substring(colon + 2));
      }
    }
    return fields;
  }

  private static String runLine(String console) {
    return console.lines().filter(l -> l.strip().startsWith("run: ")).findFirst().orElseThrow();
  }

  private static String deltaLine(String console) {
    return console
        .lines()
        .filter(l -> l.strip().startsWith("Since last green:"))
        .findFirst()
        .orElseThrow();
  }

  private static String runFixture(Path outputDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", outputDir.toString());
    try {
      return captureConsole(
          () -> {
            var request =
                LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(ApprovalDriftFixture.class));
            LauncherFactory.create().execute(request.build());
          });
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  private static String captureConsole(Runnable body) {
    var captured = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } finally {
      System.setOut(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }
}
