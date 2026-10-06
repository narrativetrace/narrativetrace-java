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
import ai.narrativetrace.tooling.init.ProjectState;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.RefreshPlanner;
import java.nio.file.Path;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * {@code narrativetraceRefreshSkills}: keeps an install current, and never starts one (D11 as
 * ruled). Wired in front of {@code classes}, so it runs on every build.
 *
 * <p>INTENT: a project that ran {@code narrativetraceInit} once should not drift a release behind
 * without noticing. What it may do is deliberately narrow — {@link RefreshPlanner} keeps only the
 * actions that REWRITE something already ours, so no build can create a skill, append a section, or
 * add an import line nobody asked for.
 *
 * <p><b>@llmNote</b> The order matters: a project with no skill directory of ours returns BEFORE
 * the carrier is reached, so a project that never ran {@code init} never resolves an artifact and
 * an offline build of it is silent. Only a project that already carries an install can produce the
 * offline warning.
 *
 * <p><b>@llmNote</b> Untracked on purpose: its inputs are the project's own tracked files, which it
 * rewrites in place. There is no output for Gradle to compare, and pretending otherwise would make
 * it UP-TO-DATE on the build after an upgrade — exactly the build that needs it.
 *
 * <p><b>@sideEffects</b> May resolve one configuration and rewrite pages the installer wrote.
 * Silent, and writes nothing, when everything is current.
 */
@UntrackedTask(
    because =
        "its inputs are the project's own tracked files, which it rewrites in place — there is no"
            + " output for Gradle to compare")
public abstract class NarrativeTraceRefreshSkillsTask extends DefaultTask {

  /** The project to refresh — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  /** The resolved carrier jar; queried only when this project carries an install of ours. */
  @Internal
  public abstract ConfigurableFileCollection getCarrier();

  /** The coordinate the carrier was asked for — what a failure has to be able to name. */
  @Input
  public abstract Property<String> getCarrierCoordinate();

  @TaskAction
  public void refresh() {
    Path project = getTargetDir().get().getAsFile().toPath();
    ProjectState state = ProjectStateReader.read(project);
    if (!RefreshPlanner.isInstalled(state)) {
      return;
    }
    SkillsCarrier.Resolution resolution =
        SkillsCarrier.resolve(getCarrier()::getFiles, getCarrierCoordinate().get());
    if (resolution.failure() != null) {
      getLogger().warn(SkillsRefreshAnnouncement.unresolved(resolution.failure()));
      return;
    }
    rewrite(RefreshPlanner.plan(state, resolution.carrier()), project);
  }

  /** Nothing stale is nothing to say: an up-to-date project leaves no line in the build log. */
  private void rewrite(InitPlan plan, Path project) {
    if (plan.isEmpty()) {
      return;
    }
    ExecutionReport report = PlanExecutor.execute(plan, project);
    Optional.ofNullable(SkillsRefreshAnnouncement.applied(report))
        .ifPresent(getLogger()::lifecycle);
    Optional.ofNullable(SkillsRefreshAnnouncement.refused(report)).ifPresent(getLogger()::warn);
  }
}
