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
package ai.narrativetrace.cli.doctor.checks;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.Finding;
import org.junit.jupiter.api.Test;

class Junit5RangeCheckTest {

  private final Junit5RangeCheck check = new Junit5RangeCheck();

  @Test
  void passesWithinRange() {
    Finding f = check.run(DoctorSnapshot.healthy());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(Junit5RangeCheck.ID);
  }

  @Test
  void passesWithTheApiCoordinateInsteadOfTheAggregate() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent("testImplementation(\"org.junit.jupiter:junit-jupiter-api:5.10.0\")")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsWhenNoJupiterDependencyIsDeclared() {
    Finding f = check.run(DoctorSnapshot.builder().build());
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("No org.junit.jupiter:junit-jupiter dependency");
  }

  @Test
  void failsOutsideTheSupportedRange() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent("testImplementation(\"org.junit.jupiter:junit-jupiter:4.13.2\")")
            .build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.fix()).contains("[5.9.0, 6.0.0)");
  }

  @Test
  void failsOnTheNextMajorLine() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent("testImplementation(\"org.junit.jupiter:junit-jupiter:6.0.0\")")
            .build();
    assertThat(check.run(s).isFailing()).isTrue();
  }
}
