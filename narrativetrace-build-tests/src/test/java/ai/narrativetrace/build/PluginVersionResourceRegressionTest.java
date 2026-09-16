/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for {@code narrativetrace-gradle-plugin/build.gradle.kts:36}: {@code
 * processResources} declares {@code moduleVersion} (the resolved {@code narrativetraceVersion}
 * property) as {@code inputs.property}, so the generated {@code narrativetrace-version.properties}
 * — what {@code VersionResolver} reads back at runtime — is regenerated whenever the version
 * changes, never served stale from a warm build directory (build-automation assessment 2026-09-14,
 * Priority 1: "a warm build could retain the previous version").
 *
 * <p>Driven against this repository's real tree with {@code -PnarrativetraceVersion} overrides
 * rather than a fixture: the input under test is one module's own build-script configuration, not
 * reusable task logic a fixture could apply elsewhere.
 */
class PluginVersionResourceRegressionTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File GENERATED_PROPERTIES =
      new File(
          PROJECT_DIR,
          "narrativetrace-gradle-plugin/build/resources/main/narrativetrace-version.properties");

  private static BuildResult gradle(String... args) {
    var allArgs = new ArrayList<>(List.of(args));
    allArgs.add("-q");
    return GradleRunner.create().withProjectDir(PROJECT_DIR).withArguments(allArgs).build();
  }

  private static String readVersion() throws IOException {
    var props = new Properties();
    try (var in = Files.newInputStream(GENERATED_PROPERTIES.toPath())) {
      props.load(in);
    }
    return props.getProperty("version");
  }

  @Test
  void bumpingTheVersionRegeneratesTheResourceWithTheNewVersion() throws IOException {
    var first =
        gradle(
            ":narrativetrace-gradle-plugin:processResources",
            "-PnarrativetraceVersion=9.9.1-regression");
    assertThat(first.task(":narrativetrace-gradle-plugin:processResources").getOutcome())
        .isEqualTo(SUCCESS);
    assertThat(readVersion()).isEqualTo("9.9.1-regression");

    var unchanged =
        gradle(
            ":narrativetrace-gradle-plugin:processResources",
            "-PnarrativetraceVersion=9.9.1-regression");
    assertThat(unchanged.task(":narrativetrace-gradle-plugin:processResources").getOutcome())
        .as("the same version rebuilt is a no-op")
        .isEqualTo(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE);

    var bumped =
        gradle(
            ":narrativetrace-gradle-plugin:processResources",
            "-PnarrativetraceVersion=9.9.2-regression");
    assertThat(bumped.task(":narrativetrace-gradle-plugin:processResources").getOutcome())
        .as("a version bump is a declared input change — never served from the warm build dir")
        .isEqualTo(SUCCESS);
    assertThat(readVersion())
        .as("the resource VersionResolver reads must carry the NEW version")
        .isEqualTo("9.9.2-regression");
  }
}
