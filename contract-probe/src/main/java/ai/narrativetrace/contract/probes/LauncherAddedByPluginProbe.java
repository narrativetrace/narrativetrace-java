/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config-shape}, since 0.2.3 — not exercised against 0.2.1. Applies the published Gradle
 * plugin in a disposable throwaway project (the same "generated project, isolated Gradle user home"
 * technique {@code scripts/verify-publication.sh}'s consumer smoke test already uses) and asks
 * Gradle for the resolved test runtime classpath — proving what a consumer's build actually
 * resolves, never what the plugin's source merely intends to add.
 */
public final class LauncherAddedByPluginProbe {

  private LauncherAddedByPluginProbe() {}

  public static String observe(String version, String gradlewPath) {
    try {
      Path project = Files.createTempDirectory("contract-probe-launcher-plugin");
      Files.writeString(
          project.resolve("settings.gradle.kts"),
          "rootProject.name = \"contract-probe-launcher-check\"\n");
      Files.writeString(
          project.resolve("build.gradle.kts"),
          "plugins {\n    java\n    id(\"ai.narrativetrace\") version \""
              + version
              + "\"\n}\n"
              + "repositories { mavenCentral() }\n");
      Path gradleHome = Files.createTempDirectory("contract-probe-launcher-plugin-home");
      ProcessBuilder builder =
          new ProcessBuilder(
                  gradlewPath,
                  "-g",
                  gradleHome.toString(),
                  "--console=plain",
                  "-q",
                  "dependencies",
                  "--configuration",
                  "testRuntimeClasspath")
              .directory(project.toFile())
              .redirectErrorStream(true);
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes());
      int exit = process.waitFor();
      return exit == 0 && output.contains("junit-platform-launcher") ? "true" : "false";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "false";
    }
  }
}
