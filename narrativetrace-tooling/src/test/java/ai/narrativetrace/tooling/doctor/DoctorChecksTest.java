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

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DoctorChecksTest {

  @Test
  void registersTwelveChecksWithStableUniqueIds() {
    assertThat(DoctorChecks.ALL).hasSize(12);
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
    assertThat(report.findings()).hasSize(12).allMatch(f -> !f.isFailing());
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
    assertThat(report.findings()).hasSize(12);
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
