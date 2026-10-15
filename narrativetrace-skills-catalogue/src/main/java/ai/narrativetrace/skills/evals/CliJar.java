/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * The built, zero-dependency {@code narrativetrace-cli} jar a trial invokes directly — by a grader
 * (as {@code $NARRATIVETRACE_CLI_JAR}) and by the {@link RegistryPreStep#NARRATIVETRACE_INIT}
 * delivery, which runs its {@code init} verb before the agent starts. {@code ./gradlew
 * :narrativetrace-cli:jar} must have run first; absent otherwise.
 *
 * <p>That one directory accumulates everything the module ever produced: the {@code -sources} and
 * {@code -javadoc} classifier jars, which carry no {@code Main-Class} at all, and one jar per
 * released version. So the choice is made twice over — runnable names only, and of those the most
 * recently built — never "whichever the filesystem happens to list first", which is how a grader
 * ends up running {@code java -jar} against a javadoc jar and failing for a reason the case never
 * touched.
 */
final class CliJar {

  private CliJar() {}

  /** The jar to run, or empty when the module has not been built. */
  static Optional<Path> locate(Path repoRoot) {
    Path libsDir = repoRoot.resolve("narrativetrace-cli/build/libs");
    if (!Files.isDirectory(libsDir)) {
      return Optional.empty();
    }
    try (var stream = Files.list(libsDir)) {
      return stream
          .filter(CliJar::isRunnableCliJar)
          .max(Comparator.comparingLong(jar -> jar.toFile().lastModified()));
    } catch (IOException e) {
      throw new UncheckedIOException("could not list " + libsDir, e);
    }
  }

  /** The jar to run, or an error that names the build step a caller forgot. */
  static Path require(Path repoRoot) {
    return locate(repoRoot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no runnable narrativetrace-cli jar under "
                        + repoRoot.resolve("narrativetrace-cli/build/libs")
                        + " — run ./gradlew :narrativetrace-cli:jar first"));
  }

  /**
   * {@code narrativetrace-cli-<version>.jar} — a classifier jar is not something {@code java -jar}
   * can run.
   */
  private static boolean isRunnableCliJar(Path path) {
    String name = path.getFileName().toString();
    return name.startsWith("narrativetrace-cli-")
        && name.endsWith(".jar")
        && !name.endsWith("-sources.jar")
        && !name.endsWith("-javadoc.jar");
  }
}
