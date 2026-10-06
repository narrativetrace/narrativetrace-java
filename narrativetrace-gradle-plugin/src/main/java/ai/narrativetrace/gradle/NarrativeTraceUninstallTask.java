/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.UninstallPlanner;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

/**
 * {@code narrativetraceUninstall}: removes exactly what {@code narrativetraceInit} wrote — skill
 * directories carrying its provenance line, the marked section, and the one {@code @AGENTS.md}
 * import line — and nothing beside it.
 *
 * <p><b>@llmNote</b> No carrier: an uninstall reads the project's own provenance lines to decide
 * what is ours, so it works offline and works after the artifact it was installed from is gone.
 * That is also why there is no {@code --force} and no {@code --vendor} here: nothing is decided,
 * only recognised.
 *
 * <p><b>@sideEffects</b> Deletes and rewrites the project's own files.
 */
@UntrackedTask(
    because =
        "it writes the project's own source tree — files a build tracks as inputs, never as this"
            + " task's outputs")
public abstract class NarrativeTraceUninstallTask extends DefaultTask {

  /** The project to clean up — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  @Input
  @Option(
      option = "diff",
      description =
          "Show the plan and the unified diff without removing anything. (Gradle's own --dry-run"
              + " skips every task, so the preview is spelled --diff here.)")
  public abstract Property<Boolean> getDiff();

  @Input
  @Option(option = "only", description = "Remove one half only: skills | agents-md.")
  public abstract Property<String> getOnly();

  @Input
  @Option(option = "json", description = "Machine-readable output instead of human text.")
  public abstract Property<Boolean> getJson();

  @TaskAction
  public void uninstall() {
    InitOptions options =
        InstallerTaskOptions.from(getDiff().get(), false, false, getOnly().get(), "");
    Path project = getTargetDir().get().getAsFile().toPath();
    InitPlan plan = UninstallPlanner.plan(ProjectStateReader.read(project), options);
    InstallerTasks.announce(
        this, InstallerTaskOutput.run(plan, project, getName(), getJson().get()));
  }
}
