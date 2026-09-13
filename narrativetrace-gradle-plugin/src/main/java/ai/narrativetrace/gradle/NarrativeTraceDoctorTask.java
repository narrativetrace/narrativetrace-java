/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import ai.narrativetrace.cli.doctor.DoctorChecks;
import ai.narrativetrace.cli.doctor.DoctorRender;
import ai.narrativetrace.cli.doctor.DoctorReport;
import ai.narrativetrace.cli.doctor.SnapshotBuilder;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * {@code narrativetraceDoctor}: runs the free {@code narrativetrace-cli} doctor's eleven checks
 * against this project and writes its JSON report. Calls {@code SnapshotBuilder}, {@code
 * DoctorChecks} and {@code DoctorRender} in-process — {@code narrativetrace-cli} is a project
 * dependency baked into this plugin's own jar (see {@code build.gradle.kts}), not an external
 * artifact resolved when the task runs, so this needs no network access beyond applying the plugin
 * itself. {@code DoctorSnapshot}'s own doc comment names this task as one of {@code
 * SnapshotBuilder}'s two callers, alongside the {@code narrativetrace-cli} {@code
 * bin/narrativetrace} launcher.
 *
 * <p>Read-only and always safe to run: like the CLI's own {@code doctor} verb, a finding is a
 * normal, expected outcome, never a build failure — this task never throws on the report's own exit
 * code, only on a real I/O failure writing the file.
 */
public abstract class NarrativeTraceDoctorTask extends DefaultTask {

  /** The project to diagnose — this project's own directory in real use. */
  @Internal
  public abstract DirectoryProperty getTargetDir();

  /** Where the JSON report lands — {@code build/narrativetrace/doctor-report.json} by default. */
  @OutputFile
  public abstract RegularFileProperty getReportFile();

  @TaskAction
  public void runDoctor() {
    var snapshot = SnapshotBuilder.build(getTargetDir().getAsFile().get().toPath());
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
