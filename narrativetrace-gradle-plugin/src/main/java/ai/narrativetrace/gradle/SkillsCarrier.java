/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.init.Carrier;
import java.io.File;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Opens the carrier the {@code narrativeTraceSkills} configuration resolved to — and turns every
 * way that can fail into a REASON rather than a stack trace.
 *
 * <p>INTENT: two callers need opposite policies for the same failure. {@code narrativetraceInit}
 * must fail the build ("you asked for this and it cannot be done"); {@code
 * narrativetraceRefreshSkills} runs in front of every {@code classes} and must warn once and carry
 * on ("you asked to build, and being offline is not a build failure"). Deciding that here would
 * force one of them to catch the other's exception.
 *
 * <p><b>@llmNote</b> The files arrive through a {@link Supplier} rather than as a Gradle {@code
 * FileCollection}, so every failure is a plain unit test rather than a nested build.
 *
 * <p><b>@llmNote</b> NO files is the ordinary shape of "offline", not a bug: the plugin reads the
 * carrier through a LENIENT artifact view, because the configuration cache resolves a task's file
 * collection while it stores the task, and a strict view would turn an offline build into a
 * configuration-time failure of {@code classes}.
 *
 * <p><b>@sideEffects</b> Resolves a configuration (which may download) and reads a jar. Writes
 * nothing.
 */
final class SkillsCarrier {

  private SkillsCarrier() {}

  /**
   * An opened carrier, or the reason there is none.
   *
   * @param carrier the carrier, or {@code null} when it could not be opened
   * @param failure why not, or {@code null} when it was opened
   */
  record Resolution(Carrier carrier, String failure) {}

  /** Resolves and opens, catching everything the attempt can throw. */
  static Resolution resolve(Supplier<Set<File>> files, String coordinate) {
    try {
      Set<File> resolved = files.get();
      if (resolved.isEmpty()) {
        return new Resolution(null, "no repository in this build provides " + coordinate);
      }
      if (resolved.size() > 1) {
        return new Resolution(
            null,
            coordinate
                + " resolved to "
                + resolved.size()
                + " files, and a carrier is exactly one jar");
      }
      return new Resolution(Carrier.open(resolved.iterator().next().toPath()), null);
    } catch (RuntimeException e) {
      return new Resolution(null, reason(e));
    }
  }

  /** A throwable with no message of its own still has to say something a person can act on. */
  private static String reason(RuntimeException e) {
    return e.getMessage() == null || e.getMessage().isBlank() ? e.toString() : e.getMessage();
  }
}
