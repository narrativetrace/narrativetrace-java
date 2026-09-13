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
package ai.narrativetrace.cli.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DoctorChecksTest {

  @Test
  void registersElevenChecksWithStableUniqueIds() {
    assertThat(DoctorChecks.ALL).hasSize(11);
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
    assertThat(report.findings()).hasSize(11).allMatch(f -> !f.isFailing());
  }

  @Test
  void anEmptySnapshotFailsAtLeastOneCheckWithExitCodeOne() {
    DoctorReport report = DoctorChecks.run(DoctorSnapshot.builder().build());
    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(report.failureCount()).isGreaterThan(0);
  }
}
