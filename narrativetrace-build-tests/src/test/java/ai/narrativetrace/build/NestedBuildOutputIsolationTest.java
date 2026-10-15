/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A nested Gradle build started from inside a test JVM shares this checkout's project directories
 * with the build that started it, so its own {@code compileJava}/{@code jar} tasks rewrite the very
 * jars that test JVM carries on its classpath — and a class not yet loaded when the rewrite lands
 * then never loads at all ({@code NoClassDefFoundError}). That is the whole of the Tier A2 replay
 * flake: the outer build compiles at one toolchain, the nested build it starts defaults to the
 * other, so every module is out of date in the nested build and gets rewritten underneath a live
 * class loader.
 *
 * <p>{@code -Pnarrativetrace.buildRoot=<absolute dir>} is the isolation: every project's build
 * directory moves under that root, mirroring the normal layout, so a nested build writes nothing
 * the build that started it reads. Asserted through the real {@code properties} task of the real
 * checkout — a task that writes nothing, so the probe itself cannot disturb what it measures.
 */
class NestedBuildOutputIsolationTest {

  private static final String BUILD_DIR_LINE = "buildDir: ";

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  private static String buildDirectoryOf(String projectPath, String... extraArguments) {
    var arguments = new ArrayList<>(List.of(projectPath + ":properties", "-q"));
    arguments.addAll(List.of(extraArguments));
    var output =
        GradleRunner.create()
            .withProjectDir(PROJECT_DIR)
            .withArguments(arguments)
            .build()
            .getOutput();
    return output
        .lines()
        .filter(line -> line.startsWith(BUILD_DIR_LINE))
        .map(line -> line.substring(BUILD_DIR_LINE.length()).trim())
        .findFirst()
        .orElseThrow(() -> new AssertionError("no build directory reported in:\n" + output));
  }

  @Test
  void anIsolatedBuildRootMovesAModulesOutputOutOfTheCheckout(@TempDir Path isolated) {
    assertThat(buildDirectoryOf(":narrativetrace-core", "-Pnarrativetrace.buildRoot=" + isolated))
        .as(
            "a nested build given an isolated build root must write its outputs there, never into"
                + " the checkout's own build directories — the test JVM that started it has those"
                + " jars on its classpath")
        .isEqualTo(isolated.resolve("narrativetrace-core/build").toString());
  }
}
