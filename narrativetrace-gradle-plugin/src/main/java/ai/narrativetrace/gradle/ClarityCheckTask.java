/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Verification task that fails the build when clarity results violate configured thresholds.
 *
 * <p>INTENT: Use this task in CI to enforce naming quality from the JSON emitted by the clarity
 * scanner or JUnit integrations.
 */
public abstract class ClarityCheckTask extends DefaultTask {

  @Internal
  public abstract RegularFileProperty getJsonFile();

  /** Stamp file written on success to enable Gradle up-to-date checking. */
  @OutputFile
  public abstract RegularFileProperty getStampFile();

  @Input
  public abstract Property<Double> getMinScore();

  @Input
  public abstract Property<Integer> getMaxHighIssues();

  /**
   * Maximum suite-level issue count tolerated before the check fails. Defaults to unlimited
   * upstream, which keeps suite issues advisory (logged, never failing).
   */
  @Input
  public abstract Property<Integer> getMaxSuiteIssues();

  @Input
  public abstract Property<Boolean> getWarnOnly();

  /** Executes the clarity check against the configured JSON result file. */
  @TaskAction
  public void check() {
    var json = loadClarityJson();
    if (json == null) return;

    var failures = new ArrayList<String>();
    checkScenarios(ClarityResultsParser.parse(json), failures);
    checkSuiteIssues(ClarityResultsParser.parseSuiteIssues(json), failures);

    reportFailures(failures, getWarnOnly().get());
    writeStamp();
  }

  private void checkScenarios(List<ClarityScenarioResult> scenarios, List<String> failures) {
    var minScore = getMinScore().get();
    var maxHighIssues = getMaxHighIssues().get();
    for (var scenario : scenarios) {
      if (scenario.overallScore() < minScore) {
        failures.add(
            String.format(
                "  '%s': score %.2f < threshold %.2f",
                scenario.name(), scenario.overallScore(), minScore));
      }
      long highCount = scenario.issues().stream().filter(i -> "HIGH".equals(i.severity())).count();
      if (highCount > maxHighIssues) {
        failures.add(
            String.format(
                "  '%s': %d HIGH issues (max %d)", scenario.name(), highCount, maxHighIssues));
      }
    }
  }

  /**
   * Logs suite-level issues (e.g. vocabulary violations) as an advisory and records a failure when
   * their count exceeds the opt-in {@code maxSuiteIssues} threshold.
   */
  private void checkSuiteIssues(
      List<ClarityScenarioResult.Issue> suiteIssues, List<String> failures) {
    if (suiteIssues.isEmpty()) {
      return;
    }
    var lines = new ArrayList<String>();
    for (var issue : suiteIssues) {
      lines.add(String.format("  %s: %s", issue.element(), issue.suggestion()));
    }
    getLogger()
        .warn(
            "NarrativeTrace: {} suite-level clarity issue(s):\n{}",
            suiteIssues.size(),
            String.join("\n", lines));
    var maxSuiteIssues = getMaxSuiteIssues().get();
    if (suiteIssues.size() > maxSuiteIssues) {
      failures.add(String.format("  %d suite issues (max %d)", suiteIssues.size(), maxSuiteIssues));
    }
  }

  private void writeStamp() {
    try {
      var stamp = getStampFile().getAsFile().get().toPath();
      Files.createDirectories(stamp.getParent());
      Files.writeString(stamp, "OK");
    } catch (IOException e) {
      throw new GradleException("Failed to write clarity check stamp: " + e.getMessage(), e);
    }
  }

  private String loadClarityJson() {
    var file = getJsonFile().getAsFile().getOrNull();
    if (file == null || !file.exists()) {
      getLogger()
          .lifecycle("NarrativeTrace: No clarity-results.json found — skipping clarity check.");
      return null;
    }
    try {
      return Files.readString(file.toPath());
    } catch (IOException e) {
      throw new GradleException("Failed to read clarity results: " + e.getMessage(), e);
    }
  }

  private void reportFailures(List<String> failures, boolean warnOnly) {
    if (failures.isEmpty()) return;
    var message = "Clarity check failed:\n" + String.join("\n", failures);
    if (warnOnly) {
      getLogger().warn(message);
    } else {
      throw new GradleException(message);
    }
  }
}
