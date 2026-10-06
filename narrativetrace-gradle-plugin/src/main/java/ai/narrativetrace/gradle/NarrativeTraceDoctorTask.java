/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.tooling.doctor.DoctorChecks;
import ai.narrativetrace.tooling.doctor.DoctorRender;
import ai.narrativetrace.tooling.doctor.DoctorReport;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code narrativetraceDoctor}: runs the free {@code narrativetrace-tooling} doctor's twelve checks
 * against this project and writes its JSON report. Calls {@code SnapshotBuilder}, {@code
 * DoctorChecks} and {@code DoctorRender} in-process — {@code narrativetrace-tooling} is a project
 * dependency baked into this plugin's own jar (see {@code build.gradle.kts}), not an external
 * artifact resolved when the task runs, so this needs no network access beyond applying the plugin
 * itself. The {@code narrativetrace-cli} launcher embeds the same library; neither entry point
 * depends on the other. {@code DoctorSnapshot}'s own doc comment names this task as one of {@code
 * SnapshotBuilder}'s two callers, alongside the {@code narrativetrace-cli} {@code
 * bin/narrativetrace} launcher.
 *
 * <p>Read-only and always safe to run: like the CLI's own {@code doctor} verb, a finding is a
 * normal, expected outcome, never a build failure — this task never throws on the report's own exit
 * code, only on a real I/O failure writing the file.
 *
 * <p><b>@llmNote</b> One of the twelve checks needs the agent-skills carrier this project resolves,
 * so this task reads the same {@code narrativeTraceSkills} configuration {@code narrativetraceInit}
 * does — through the same LENIENT artifact view, and with the opposite policy on failure: {@code
 * init} fails the build with the coordinate named, because a person asked it to install something;
 * the doctor carries on and lets the skills check report that it cannot tell. A read-only diagnosis
 * that cannot run offline would be no diagnosis at all.
 */
public abstract class NarrativeTraceDoctorTask extends DefaultTask {

  /** The project to diagnose — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  /** Where the JSON report lands — {@code build/narrativetrace/doctor-report.json} by default. */
  @OutputFile
  public abstract RegularFileProperty getReportFile();

  /**
   * The resolved agent-skills carrier. {@code @Internal}: the report is this task's output and the
   * carrier is read only to compare a coordinate, so declaring it as an input would buy nothing but
   * a path-sensitivity decision nobody reads.
   */
  @Internal
  public abstract ConfigurableFileCollection getCarrier();

  /**
   * The coordinate the carrier was asked for — what "could not resolve it" has to be able to say.
   */
  @Input
  public abstract Property<String> getCarrierCoordinate();

  @TaskAction
  public void runDoctor() {
    SkillsCarrier.Resolution resolution =
        SkillsCarrier.resolve(getCarrier()::getFiles, getCarrierCoordinate().get());
    var snapshot =
        SnapshotBuilder.build(getTargetDir().getAsFile().get().toPath(), resolution.carrier());
    DoctorReport report = DoctorChecks.run(snapshot);
    var reportFile = getReportFile().getAsFile().get();
    try {
      Files.createDirectories(reportFile.getParentFile().toPath());
      Files.writeString(reportFile.toPath(), DoctorRender.renderJson(report));
    } catch (IOException e) {
      throw new GradleException(
          "narrativetraceDoctor could not write its report to " + reportFile, e);
    }
    getLogger().lifecycle(DoctorRender.renderHuman(report));
    if (!report.allPassed()) {
      getLogger()
          .lifecycle(
              "NarrativeTrace doctor report written to "
                  + reportFile
                  + " — findings above, build not failed by this task.");
    }
  }
}
