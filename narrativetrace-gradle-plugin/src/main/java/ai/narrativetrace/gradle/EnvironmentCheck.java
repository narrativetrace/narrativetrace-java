/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.ArrayList;
import java.util.Optional;
import org.gradle.api.GradleException;

/**
 * Verifies that the consumer's build environment can actually run NarrativeTrace.
 *
 * <p>INTENT: Fail at plugin-apply time with one sentence naming the requirement, instead of letting
 * the build reach a compile error from the library ("cannot find symbol: record") or a Gradle API
 * error that says nothing about NarrativeTrace. The floors are Gradle 8.0 and Java 17 — the
 * language level the whole codebase is written in (79 records, sealed interfaces, pattern-matching
 * switches), not a preference.
 *
 * <p><b>@llmNote</b> Deliberately permissive about what it cannot read: an unparseable Gradle
 * version or an unknown Java version passes. A version check that guesses wrong must not be the
 * thing that stops someone's build.
 */
final class EnvironmentCheck {

  /** Java version sentinel for "the build did not tell us" — never a failure. */
  static final int UNKNOWN_JAVA = -1;

  private static final int MIN_GRADLE_MAJOR = 8;
  private static final int MIN_JAVA = 17;

  private EnvironmentCheck() {}

  /** Throws with the combined message when the environment is unsupported. */
  static void verify(String gradleVersion, int javaMajor) {
    problem(gradleVersion, javaMajor)
        .ifPresent(
            message -> {
              throw new GradleException(message);
            });
  }

  /**
   * The problem with this environment, or empty when it is supported.
   *
   * @param gradleVersion the running Gradle version, e.g. {@code "8.14.2"} or {@code "8.5-rc-2"}
   * @param javaMajor the Java feature release the build compiles with, or {@link #UNKNOWN_JAVA}
   */
  static Optional<String> problem(String gradleVersion, int javaMajor) {
    if (gradleVersion == null) {
      throw new IllegalArgumentException("gradleVersion must not be null");
    }
    var problems = new ArrayList<String>();
    majorOf(gradleVersion)
        .filter(major -> major < MIN_GRADLE_MAJOR)
        .ifPresent(
            major ->
                problems.add(
                    "requires Gradle "
                        + MIN_GRADLE_MAJOR
                        + ".0 or newer (running "
                        + gradleVersion
                        + ")"));
    if (javaMajor != UNKNOWN_JAVA && javaMajor < MIN_JAVA) {
      problems.add(
          "requires Java " + MIN_JAVA + " or newer (this build targets " + javaMajor + ")");
    }
    if (problems.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of("The ai.narrativetrace plugin " + String.join(", and ", problems) + ".");
  }

  /** The leading integer of a Gradle version string; empty when it does not start with one. */
  private static Optional<Integer> majorOf(String gradleVersion) {
    int end = 0;
    while (end < gradleVersion.length() && Character.isDigit(gradleVersion.charAt(end))) {
      end++;
    }
    if (end == 0) {
      return Optional.empty();
    }
    return Optional.of(Integer.parseInt(gradleVersion.substring(0, end)));
  }
}
