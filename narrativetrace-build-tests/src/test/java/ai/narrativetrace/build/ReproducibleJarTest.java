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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the reproducible-archives convention ({@code ReproducibleArchives}, buildSrc) as
 * behaviour, not just as two flags read back: building the same jar twice from clean, with nothing
 * else changed, must produce byte-identical archives (build-automation assessment 2026-09-14,
 * Priority 1/4).
 *
 * <p>Driven in a throwaway fixture build that applies the real convention object and compiles
 * {@code narrativetrace-api}'s own sources (read in place, never copied — its architecture rule of
 * zero dependencies makes it the one module a bare {@code java} plugin can build, and its package
 * tree is large enough that entry ORDER, not just entry timestamps, is actually exercised). The
 * fixture's build directory is its own: a {@code clean} here deletes nothing outside the temp
 * directory.
 *
 * <p>WHY NOT the live module (regression, 2026-09-16): this test used to run {@code
 * :narrativetrace-api:clean :narrativetrace-api:jar} through TestKit against THIS repository while
 * the very build running it was still in flight. On a cold tree, {@code
 * :narrativetrace-build-tests:test} is scheduled before {@code
 * :narrativetrace-core:compileTestJava} — so the nested {@code clean} deleted {@code
 * narrativetrace-api/build}, test fixtures included, out from under a consumer that had not
 * compiled yet, and core's test compilation failed with 77 {@code cannot find symbol:
 * TestSpanContext}. A test may read the build it runs inside; it may never delete part of it.
 */
class ReproducibleJarTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  /** buildSrc's classes and their runtime dependencies, for a fixture build to load them from. */
  private static final List<String> BUILD_SRC_CLASSPATH =
      Arrays.asList(System.getProperty("buildSrcClasspath").split(File.pathSeparator));

  private static BuildResult gradle(File projectDir, String... args) {
    var allArgs = new ArrayList<>(List.of(args));
    allArgs.add("-q");
    return GradleRunner.create().withProjectDir(projectDir).withArguments(allArgs).build();
  }

  @Test
  void theSameJarBuiltTwiceFromCleanIsByteIdentical(@TempDir Path fixture) throws IOException {
    writeFixture(fixture);

    var first = buildJarBytes(fixture);
    assertThat(first).as("first build must produce the jar").isNotEmpty();

    var second = buildJarBytes(fixture);

    assertThat(second)
        .as("a clean rebuild from the same source must be byte-for-byte identical")
        .isEqualTo(first);
  }

  private static byte[] buildJarBytes(Path fixture) throws IOException {
    gradle(fixture.toFile(), "clean", "jar");
    return Files.readAllBytes(fixture.resolve("build/libs/fixture.jar"));
  }

  private static void writeFixture(Path fixture) throws IOException {
    var apiSources = groovyPath(new File(PROJECT_DIR, "narrativetrace-api/src/main/java"));
    Files.writeString(fixture.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
    Files.writeString(
        fixture.resolve("build.gradle"),
        "buildscript { dependencies { classpath files("
            + BUILD_SRC_CLASSPATH.stream()
                .map(p -> "'" + p.replace("\\", "/") + "'")
                .collect(Collectors.joining(", "))
            + ") } }\n"
            + "apply plugin: 'java'\n"
            + "sourceSets.main.java.srcDirs = ['"
            + apiSources
            + "']\n"
            // Groovy reaches a Kotlin `object`'s methods through INSTANCE.
            + "ai.narrativetrace.build.ReproducibleArchives.INSTANCE.apply(project)\n");
  }

  private static String groovyPath(File file) {
    return file.getAbsolutePath().replace("\\", "/");
  }
}
