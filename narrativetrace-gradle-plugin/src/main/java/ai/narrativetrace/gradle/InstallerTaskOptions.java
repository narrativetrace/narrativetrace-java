/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import org.gradle.api.GradleException;

/**
 * Turns {@code narrativetraceInit}'s command-line options into what the installer library takes.
 *
 * <p>INTENT: the one place a typed flag becomes a decision, so a wrong value fails the build with
 * the accepted values named instead of quietly falling through to a default. Pure — no Gradle task,
 * no filesystem — which is why every value and every mistake is a plain unit test.
 *
 * <p><b>@llmNote</b> The preview flag is spelled {@code --diff} on the task, not {@code --dry-run}:
 * Gradle owns {@code --dry-run} as a built-in that skips EVERY task, so a task option of that name
 * would never be read and the task would never run. {@link InitOptions#dryRun()} is what it sets,
 * and the CLI verb keeps the {@code --dry-run} spelling, where nothing shadows it.
 */
final class InstallerTaskOptions {

  private InstallerTaskOptions() {}

  /**
   * What an installer task was asked for.
   *
   * <p>Values are checked in the order they are written here, so a person who mistypes two flags at
   * once is told about the FIRST — the same "first error wins" rule the launcher's own parser
   * follows. Stated as two statements rather than left to the order a record constructor happens to
   * evaluate its arguments in: that order is Java's, not a decision anyone made.
   */
  static InitOptions from(
      boolean diff, boolean writeExisting, boolean force, String only, String vendor) {
    Scope scope = scope(only);
    Vendor claude = vendor(vendor);
    return new InitOptions(diff, writeExisting, force, scope, claude);
  }

  private static Scope scope(String only) {
    return switch (clean(only)) {
      case "" -> Scope.BOTH;
      case "skills" -> Scope.SKILLS;
      case "agents-md" -> Scope.AGENTS_MD;
      default -> throw refusal("--only", only, "skills, agents-md");
    };
  }

  private static Vendor vendor(String vendor) {
    return switch (clean(vendor)) {
      case "" -> Vendor.AUTO;
      case "claude" -> Vendor.ON;
      case "none" -> Vendor.OFF;
      default -> throw refusal("--vendor", vendor, "claude, none");
    };
  }

  /** An unset option and an empty one mean the same thing: the default. */
  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static GradleException refusal(String option, String value, String accepted) {
    return new GradleException("Invalid " + option + " '" + value + "'. Valid values: " + accepted);
  }
}
