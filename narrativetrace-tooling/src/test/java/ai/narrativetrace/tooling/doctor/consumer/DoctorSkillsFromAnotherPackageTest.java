/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.doctor.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorChecks;
import ai.narrativetrace.tooling.doctor.DoctorReport;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.InitPlanner;
import ai.narrativetrace.tooling.init.PlanExecutor;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The doctor's skills check as an ENTRY POINT sees it: another package, the real published carrier,
 * a real directory that the real installer wrote.
 *
 * <p>These are the cases that would catch the two halves DISAGREEING — the installer writing a page
 * the doctor does not recognise as ours, or the doctor looking somewhere the installer never wrote.
 * A same-package unit test over a hand-built snapshot cannot see that class of defect at all,
 * because it never runs the two against the same bytes.
 */
class DoctorSkillsFromAnotherPackageTest {

  private static final String ID = "config.skills-installed";

  private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir"));
  private static final String VERSION = System.getProperty("narrativetrace.buildVersion");

  private static Carrier realCarrier() {
    return Carrier.open(
        PROJECT_DIR.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + VERSION + ".jar"));
  }

  private static void install(Path project) {
    Carrier carrier = realCarrier();
    InitPlan plan =
        InitPlanner.plan(ProjectStateReader.read(project), carrier, InitOptions.defaults());
    PlanExecutor.execute(plan, project);
  }

  private static Finding skillsFinding(Path project, Carrier carrier) {
    DoctorReport report = DoctorChecks.run(SnapshotBuilder.build(project, carrier));
    return report.findings().stream()
        .filter(finding -> finding.id().equals(ID))
        .findFirst()
        .orElseThrow(() -> new AssertionError("the doctor registered no " + ID + " check"));
  }

  @Test
  void whatTheInstallerJustWroteIsWhatTheDoctorCallsInstalled(@TempDir Path project) {
    install(project);

    Finding finding = skillsFinding(project, realCarrier());

    assertThat(finding.isFailing()).isFalse();
    assertThat(finding.message()).contains("ai.narrativetrace:narrativetrace-skills:" + VERSION);
  }

  @Test
  void anUninstalledProjectFailsTheCheckAndTheDoctorExitsOne(@TempDir Path project) {
    DoctorReport report = DoctorChecks.run(SnapshotBuilder.build(project, realCarrier()));

    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(report.findings())
        .filteredOn(Finding::isFailing)
        .extracting(Finding::id)
        .contains(ID);
  }

  /** Offline: the same untouched project, with no carrier to compare against, is not a defect. */
  @Test
  void theSameProjectPassesWhenNoCarrierCouldBeResolved(@TempDir Path project) {
    assertThat(DoctorChecks.run(SnapshotBuilder.build(project)).exitCode()).isZero();
  }

  @Test
  void deletingOneInstalledSkillIsReportedByName(@TempDir Path project) throws IOException {
    install(project);
    String removed = realCarrier().skills().get(0).name();
    deleteRecursively(project.resolve(".agents/skills").resolve(removed));

    Finding finding = skillsFinding(project, realCarrier());

    assertThat(finding.isFailing()).isTrue();
    assertThat(finding.message()).contains(removed);
  }

  @Test
  void aPageStampedByAnOlderCarrierIsReportedAsStale(@TempDir Path project) throws IOException {
    install(project);
    Path page =
        project
            .resolve(".agents/skills")
            .resolve(realCarrier().skills().get(0).name())
            .resolve("SKILL.md");
    Files.writeString(page, Files.readString(page).replace(VERSION, "0.0.1"));

    Finding finding = skillsFinding(project, realCarrier());

    assertThat(finding.isFailing()).isTrue();
    assertThat(finding.message())
        .contains("ai.narrativetrace:narrativetrace-skills:0.0.1")
        .contains("resolves ai.narrativetrace:narrativetrace-skills:" + VERSION);
  }

  /** A page somebody else wrote carries no provenance line, so it is reported and never counted. */
  @Test
  void aForeignPageWhereASkillBelongsIsReportedAsNotOurs(@TempDir Path project) throws IOException {
    install(project);
    Path page =
        project
            .resolve(".agents/skills")
            .resolve(realCarrier().skills().get(0).name())
            .resolve("SKILL.md");
    Files.writeString(page, "---\nname: mine\n---\n\nmine, thanks\n");

    Finding finding = skillsFinding(project, realCarrier());

    assertThat(finding.isFailing()).isTrue();
    assertThat(finding.message()).contains("not ours");
  }

  /** The finding class has no fixing skill: the skills are exactly what is not there. */
  @Test
  void theFindingNamesNoSkillToFollow(@TempDir Path project) {
    assertThat(skillsFinding(project, realCarrier()).skill()).isNull();
  }

  private static void deleteRecursively(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }
}
