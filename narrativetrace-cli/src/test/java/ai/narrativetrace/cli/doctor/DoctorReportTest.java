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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DoctorReportTest {

  private static final Finding PASSING = Finding.pass("id.pass", "ok", "https://x");
  private static final Finding FAILING = Finding.fail("id.fail", "bad", "fix it", "https://x");

  @Test
  void allPassedTracksExitCode() {
    DoctorReport clean = new DoctorReport(List.of(PASSING), 0);
    assertThat(clean.allPassed()).isTrue();
    assertThat(clean.failureCount()).isZero();
  }

  @Test
  void countsFailures() {
    DoctorReport dirty = new DoctorReport(List.of(PASSING, FAILING, FAILING), 1);
    assertThat(dirty.allPassed()).isFalse();
    assertThat(dirty.failureCount()).isEqualTo(2);
  }

  @Test
  void rejectsAnInvalidExitCode() {
    assertThatThrownBy(() -> new DoctorReport(List.of(PASSING), 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findingsAreDefensivelyCopied() {
    DoctorReport report = new DoctorReport(List.of(PASSING), 0);
    assertThat(report.findings()).containsExactly(PASSING);
  }
}
