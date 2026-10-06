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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The built {@code narrativetrace-cli} jar, run as a real {@code java -jar} subprocess — shared by
 * every build test that has to prove something about the PACKAGED launcher rather than about its
 * classes.
 *
 * <p>INTENT: a classpath that is right when the tests run and wrong inside the jar is the failure
 * mode these tests exist to catch, so the invocation has to be the one a person types. Two test
 * classes needed the identical three helpers (locate the jar, build the argv, capture the merged
 * streams); one home for them means a change to how the launcher is invoked cannot be made in one
 * place and forgotten in the other.
 *
 * @llmNote The jar has to exist: the test task declares {@code :narrativetrace-cli:jar} as a
 *     dependency, and {@link #jar()} asserts rather than returning a missing file, so a broken task
 *     wiring reads as "the jar is not there" instead of as a mysterious process failure.
 */
final class CliJar {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  static final String BUILD_VERSION = System.getProperty("narrativetrace.buildVersion");

  /** One launcher invocation: its exit code and its merged standard output and error. */
  record Run(int exitCode, String output) {}

  private CliJar() {}

  static File jar() {
    File jar =
        new File(
            PROJECT_DIR,
            "narrativetrace-cli/build/libs/narrativetrace-cli-" + BUILD_VERSION + ".jar");
    assertThat(jar).as("the CLI jar — the test task declares it as a dependency").isFile();
    return jar;
  }

  /** The repository root, for a test that reads a committed page or a built artifact. */
  static Path repoRoot() {
    return PROJECT_DIR.toPath();
  }

  static Run run(Path workingDir, String... args) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of(javaBinary(), "-jar", jar().getPath()));
    command.addAll(List.of(args));
    Process process =
        new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), output);
  }

  /** The JVM running these tests, never whatever {@code java} the PATH happens to resolve. */
  private static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }
}
