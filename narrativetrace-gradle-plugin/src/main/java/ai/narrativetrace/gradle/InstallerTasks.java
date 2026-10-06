/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.Task;

/**
 * The one thing {@code narrativetraceInit} and {@code narrativetraceUninstall} do identically:
 * print what happened, then fail if anything was refused.
 *
 * <p>INTENT: the two tasks differ only in which plan they build. Sharing the ending here keeps that
 * true, so a change to the exit semantics cannot reach one task and miss the other — the divergence
 * between parallel paths this repository has been bitten by before.
 */
final class InstallerTasks {

  private InstallerTasks() {}

  /**
   * What to say when Gradle's own {@code --dry-run} swallowed the run.
   *
   * <p>{@code gradle <task> --dry-run} sets the BUILT-IN flag, which skips every task, so the task
   * never executes and prints nothing at all. Until a person learns that, the obvious command for
   * "show me what this would do" looks broken — so the plugin says it, at configuration time, while
   * it still can.
   */
  static String dryRunHint(String taskName) {
    return "NarrativeTrace: --dry-run is Gradle's own flag and skips every task, so "
        + taskName
        + " will not run. For the plan and the unified diff, use: ./gradlew "
        + taskName
        + " --diff";
  }

  /** Logs the outcome, and fails the task when the plan refused to do something. */
  static void announce(Task task, InstallerTaskOutput.Outcome outcome) {
    task.getLogger().lifecycle(outcome.text());
    Optional.ofNullable(outcome.refusal())
        .ifPresent(
            refusal -> {
              throw new GradleException(refusal);
            });
  }
}
