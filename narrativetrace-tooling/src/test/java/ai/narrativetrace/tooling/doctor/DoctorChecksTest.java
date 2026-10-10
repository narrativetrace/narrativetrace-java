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
package ai.narrativetrace.tooling.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.frameworks.CheckBinding;
import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DoctorChecksTest {

  @Test
  void registersTwentyChecksWithStableUniqueIds() {
    assertThat(DoctorChecks.ALL).hasSize(20);
    Set<String> ids = new HashSet<>();
    for (var check : DoctorChecks.ALL) {
      Finding f = check.run(DoctorSnapshot.healthy());
      assertThat(ids.add(f.id())).as("duplicate id " + f.id()).isTrue();
    }
  }

  @Test
  void aHealthySnapshotPassesEveryCheckWithExitCodeZero() {
    DoctorReport report = DoctorChecks.run(DoctorSnapshot.healthy());
    assertThat(report.exitCode()).isZero();
    assertThat(report.allPassed()).isTrue();
    assertThat(report.findings()).hasSize(20).allMatch(f -> !f.isFailing());
  }

  /**
   * The console app that {@code documentation/llms.txt}'s install block builds declares no JUnit,
   * registers no extension and renders no output yet. Every one of those absences is legitimate, so
   * the doctor reports nothing to hold rather than a defect: a check fails a project for what it
   * got WRONG, never for what it does not use.
   */
  @Test
  void aProjectThatLacksEveryOptionalPiecePassesEveryCheckWithExitCodeZero() {
    DoctorReport report = DoctorChecks.run(DoctorSnapshot.builder().build());
    assertThat(report.exitCode()).isZero();
    assertThat(report.failureCount()).isZero();
    assertThat(report.findings()).hasSize(20);
  }

  /**
   * Every framework-table row that earns a wiring check is registered exactly once, in table order,
   * after the configuration checks and before the traps — a new row reaches the doctor without
   * anybody editing this registry.
   */
  @Test
  void registersOneWiringCheckPerFrameworkRowInTableOrder() {
    List<String> ids =
        DoctorChecks.ALL.stream().map(c -> c.run(DoctorSnapshot.healthy()).id()).toList();
    List<String> rowChecks = FrameworkTable.wiringCheckIds();
    assertThat(rowChecks).hasSize(7);
    int first = ids.indexOf(rowChecks.get(0));
    assertThat(ids.subList(first, first + rowChecks.size())).isEqualTo(rowChecks);
    assertThat(ids.get(first - 1)).isEqualTo("config.skills-installed");
    assertThat(ids.get(first + rowChecks.size())).isEqualTo("trap.silent-sink");
  }

  /** A row bound to an existing check names one the doctor really runs. */
  @Test
  void everyRowBoundToAnExistingCheckNamesARegisteredOne() {
    List<String> ids =
        DoctorChecks.ALL.stream().map(c -> c.run(DoctorSnapshot.healthy()).id()).toList();
    FrameworkTable.ROWS.stream()
        .map(FrameworkRow::check)
        .filter(CheckBinding.ExistingCheck.class::isInstance)
        .map(c -> ((CheckBinding.ExistingCheck) c).id())
        .forEach(id -> assertThat(ids).contains(id));
  }

  @Test
  void aRealDefectStillFailsWithExitCodeOne() {
    DoctorSnapshot outOfRange =
        DoctorSnapshot.healthy().toBuilder()
            .buildFileContent("testImplementation(\"org.junit.jupiter:junit-jupiter:4.13.2\")")
            .build();
    DoctorReport report = DoctorChecks.run(outOfRange);
    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(report.failureCount()).isGreaterThan(0);
    assertThat(report.findings())
        .filteredOn(Finding::isFailing)
        .extracting(Finding::id)
        .contains("toolchain.junit5-range");
  }

  /**
   * Every registered check has a DECISION in the skill table — a skill, or a recorded "none". A new
   * check that nobody classified would otherwise ship carrying a silent null, which reads to an
   * agent exactly like "no skill fixes this".
   */
  @Test
  void everyRegisteredCheckIsClassifiedInTheSkillTable() {
    for (var check : DoctorChecks.ALL) {
      String id = check.run(DoctorSnapshot.healthy()).id();
      assertThat(FindingSkills.knows(id)).as("no skill decision for " + id).isTrue();
    }
  }
}
