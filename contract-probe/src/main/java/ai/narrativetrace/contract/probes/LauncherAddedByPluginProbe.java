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
import java.util.List;

/**
 * {@code config-shape}, since 0.2.3 — not exercised against 0.2.1. Applies the published Gradle
 * plugin in a disposable throwaway project (the same "generated project, isolated Gradle user home"
 * technique {@code scripts/verify-publication.sh}'s consumer smoke test already uses) and asks
 * Gradle for the resolved test runtime classpath — proving what a consumer's build actually
 * resolves, never what the plugin's source merely intends to add.
 *
 * <p>The generated project's {@code settings.gradle.kts} mirrors the DOCUMENTED consumer snippet
 * (README.md / installation-guide.md "Give Maven Central the first look in {@code
 * pluginManagement}"): {@code mavenCentral()} before {@code gradlePluginPortal()}. That is what
 * lets this probe prove what the docs say a consumer should do, rather than what a bare default
 * {@code pluginManagement} (Portal only) would do — the Portal's {@code /m2} proxy answers 404 for
 * a plugin marker still awaiting Gradle's approval of a new id while Central already serves it
 * (B-65), so a Portal-only consumer cannot resolve the plugin at all on a fresh release.
 *
 * <p>{@link #observe} distinguishes three outcomes rather than collapsing every non-zero exit into
 * {@code "false"}: {@code "true"} (launcher resolved), {@code "false"} (Gradle succeeded, no
 * launcher on the resolved classpath — a real "not added" reading), and an {@code "error: "}
 * verdict naming the first Gradle error line when the build itself fails to resolve — "the plugin
 * id is unresolvable" and "resolved, no launcher" must never read the same (release rule 2).
 */
public final class LauncherAddedByPluginProbe {

  private LauncherAddedByPluginProbe() {}

  public static String observe(String version, String gradlewPath) {
    try {
      Path project = Files.createTempDirectory("contract-probe-launcher-plugin");
      Files.writeString(
          project.resolve("settings.gradle.kts"),
          "rootProject.name = \"contract-probe-launcher-check\"\n"
              + "pluginManagement {\n"
              + "    repositories {\n"
              + "        mavenCentral()\n"
              + "        gradlePluginPortal()\n"
              + "    }\n"
              + "}\n");
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
      if (exit != 0) {
        return "error: " + firstGradleErrorLine(output);
      }
      return output.contains("junit-platform-launcher") ? "true" : "false";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "error: interrupted while waiting for gradlew";
    }
  }

  /**
   * The line right after Gradle's {@code * What went wrong:} banner, the closest thing to a
   * human-readable cause in {@code --console=plain -q} output; falls back to the first non-blank
   * output line when that banner is absent (a failure before Gradle prints its own report, e.g. a
   * missing {@code gradlew}).
   */
  private static String firstGradleErrorLine(String output) {
    List<String> lines = output.lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).trim().equals("* What went wrong:")) {
        for (int j = i + 1; j < lines.size(); j++) {
          String candidate = lines.get(j).trim();
          if (!candidate.isEmpty()) {
            return candidate;
          }
        }
      }
    }
    return lines.stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .findFirst()
        .orElse("(no output)");
  }
}
