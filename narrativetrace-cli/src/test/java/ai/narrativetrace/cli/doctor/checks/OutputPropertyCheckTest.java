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

class OutputPropertyCheckTest {

  private final OutputPropertyCheck check = new OutputPropertyCheck();

  @Test
  void passesWhenUnset() {
    Finding f = check.run(DoctorSnapshot.builder().build());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(OutputPropertyCheck.ID);
    assertThat(f.message()).contains("unset");
  }

  @Test
  void passesTrueOrFalseFromSystemProperty() {
    assertThat(
            check
                .run(
                    DoctorSnapshot.builder()
                        .putSystemProperty("narrativetrace.output", "TRUE")
                        .build())
                .status())
        .isEqualTo(Finding.Status.PASS);
    assertThat(
            check
                .run(
                    DoctorSnapshot.builder()
                        .putSystemProperty("narrativetrace.output", "false")
                        .build())
                .status())
        .isEqualTo(Finding.Status.PASS);
  }

  @Test
  void passesFromGradlePropertyOverEnv() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putGradleProperty("narrativetrace.output", "true")
            .putEnv("NARRATIVETRACE_OUTPUT", "garbage")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void passesFromEnvWhenNothingElseIsSet() {
    DoctorSnapshot s = DoctorSnapshot.builder().putEnv("NARRATIVETRACE_OUTPUT", "false").build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsOnAnInvalidValue() {
    DoctorSnapshot s =
        DoctorSnapshot.builder().putSystemProperty("narrativetrace.output", "yes").build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("yes");
  }

  @Test
  void failsOnAnInvalidEnvValue() {
    DoctorSnapshot s = DoctorSnapshot.builder().putEnv("NARRATIVETRACE_OUTPUT", "1").build();
    assertThat(check.run(s).isFailing()).isTrue();
  }
}
