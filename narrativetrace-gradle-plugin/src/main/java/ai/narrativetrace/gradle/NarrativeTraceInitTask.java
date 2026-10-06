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
import ai.narrativetrace.tooling.init.InitPlanner;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

/**
 * {@code narrativetraceInit}: copies the NarrativeTrace agent skills into this project and writes
 * one marked section into {@code AGENTS.md}. A thin caller over the free {@code
 * narrativetrace-tooling} installer library, exactly the shape {@link NarrativeTraceDoctorTask} has
 * over the doctor — the library is a project dependency baked into this plugin's own jar, so every
 * decision here is already unit-tested somewhere that needs no Gradle.
 *
 * <p><b>@llmNote</b> The SKILLS themselves are not in this jar: they are resolved at task time from
 * the invisible, non-transitive {@code narrativeTraceSkills} configuration, on the same version
 * every other family artifact gets. Resolution happens when the task RUNS — never while the build
 * is configured, which is what keeps the configuration cache green.
 *
 * <p><b>@llmNote</b> The preview flag is {@code --diff}, not {@code --dry-run}: Gradle owns {@code
 * --dry-run} as a built-in that skips every task, so a task option of that name would never be read
 * and this task would never run. It sets the same {@link InitOptions#dryRun()} the CLI's {@code
 * --dry-run} does.
 *
 * <p><b>@llmNote</b> A refusal FAILS this task, unlike the doctor's findings. A person ran a
 * command that changes their files; a half-applied install mentioned only in a log line is worse
 * than a red build naming the flag that would allow the rest.
 *
 * <p><b>@sideEffects</b> Resolves one configuration (which may download once) and writes the
 * project's own files.
 */
@UntrackedTask(
    because =
        "it writes the project's own source tree — files a build tracks as inputs, never as this"
            + " task's outputs")
public abstract class NarrativeTraceInitTask extends DefaultTask {

  /** The project to install into — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  /**
   * The resolved carrier jar. {@code @Internal} on purpose: this task is untracked, so declaring it
   * as an input would buy no up-to-date checking and would only add a path-sensitivity decision
   * nothing reads.
   */
  @Internal
  public abstract ConfigurableFileCollection getCarrier();

  /** The coordinate the carrier was asked for — what a failure has to be able to name. */
  @Input
  public abstract Property<String> getCarrierCoordinate();

  @Input
  @Option(
      option = "diff",
      description =
          "Show the plan and the unified diff without writing anything. (Gradle's own --dry-run"
              + " skips every task, so the preview is spelled --diff here.)")
  public abstract Property<Boolean> getDiff();

  @Input
  @Option(
      option = "write-existing",
      description = "Permission to touch an AGENTS.md or CLAUDE.md that is already there.")
  public abstract Property<Boolean> getWriteExisting();

  @Input
  @Option(
      option = "force",
      description = "Permission to overwrite a skill directory somebody else owns.")
  public abstract Property<Boolean> getForce();

  @Input
  @Option(option = "only", description = "Install one half only: skills | agents-md.")
  public abstract Property<String> getOnly();

  @Input
  @Option(
      option = "vendor",
      description = "Force the vendor flavour on or off: claude | none. Detected by default.")
  public abstract Property<String> getVendor();

  @Input
  @Option(option = "json", description = "Machine-readable output instead of human text.")
  public abstract Property<Boolean> getJson();

  @TaskAction
  public void install() {
    InitOptions options =
        InstallerTaskOptions.from(
            getDiff().get(),
            getWriteExisting().get(),
            getForce().get(),
            getOnly().get(),
            getVendor().get());
    Path project = getTargetDir().get().getAsFile().toPath();
    SkillsCarrier.Resolution resolution =
        SkillsCarrier.resolve(getCarrier()::getFiles, getCarrierCoordinate().get());
    if (resolution.failure() != null) {
      throw new GradleException(
          getName()
              + " could not resolve the NarrativeTrace skills carrier: "
              + resolution.failure());
    }
    InitPlan plan =
        InitPlanner.plan(ProjectStateReader.read(project), resolution.carrier(), options);
    InstallerTasks.announce(
        this, InstallerTaskOutput.run(plan, project, getName(), getJson().get()));
  }
}
