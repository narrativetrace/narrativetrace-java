/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.init.ExecutionReport;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.PlanExecutor;
import ai.narrativetrace.tooling.init.PlanRenderer;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Applies or previews an installer plan and says what happened — everything {@code
 * narrativetraceInit} and {@code narrativetraceUninstall} do beyond deciding WHICH plan.
 *
 * <p>INTENT: keeps the two task classes to a handful of lines and keeps this — where the exit
 * semantics live — a plain unit test against a temp directory, with no Gradle task and no nested
 * build.
 *
 * <p><b>@llmNote</b> A refusal FAILS the task (D12 at the Gradle surface): a person is at the
 * keyboard, and a half-applied install reported only in a log line is worse than a red build naming
 * the flag that would allow it. A preview never fails — a refusal there is something shown, not
 * something that happened. The other actions are applied either way; one bad file must not cost a
 * project its other nine.
 *
 * <p><b>@sideEffects</b> Writes the project's files, unless the plan is a preview.
 */
final class InstallerTaskOutput {

  private InstallerTaskOutput() {}

  /**
   * What a task should print, and what it should fail with.
   *
   * @param text the whole of what to log, already rendered
   * @param refusal the failure message, or {@code null} when nothing was refused
   */
  record Outcome(String text, String refusal) {}

  /**
   * Previews or applies {@code plan} against {@code project}. Both forms come from {@link
   * PlanRenderer#render} so this task and the {@code narrativetrace} launcher cannot drift apart on
   * what a preview shows.
   */
  static Outcome run(InitPlan plan, Path project, String taskName, boolean json) {
    if (plan.dryRun()) {
      return new Outcome(PlanRenderer.render(plan, json), null);
    }
    ExecutionReport report = PlanExecutor.execute(plan, project);
    return new Outcome(
        PlanRenderer.render(report, json), report.hasRefusals() ? refusal(taskName, report) : null);
  }

  /** Every refusal, each on its own line, with the reason that names the flag that allows it. */
  private static String refusal(String taskName, ExecutionReport report) {
    return report.results().stream()
        .filter(result -> result.status() == ExecutionReport.Status.REFUSED)
        .map(result -> "  - " + result.action().path() + ": " + result.detail())
        .collect(Collectors.joining("\n", taskName + " refused to change this project:\n", "\n"));
  }
}
