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

class JdkVersionCheckTest {

  private final JdkVersionCheck check = new JdkVersionCheck();

  @Test
  void passesOnAModernJdk() {
    DoctorSnapshot s = DoctorSnapshot.healthy().toBuilder().runningJavaVersion("21.0.1").build();
    Finding f = check.run(s);
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(JdkVersionCheck.ID);
  }

  @Test
  void passesOnExactlyTheMinimum() {
    Finding f =
        check.run(DoctorSnapshot.healthy().toBuilder().runningJavaVersion("17.0.0").build());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsBelowTheMinimum() {
    Finding f =
        check.run(DoctorSnapshot.healthy().toBuilder().runningJavaVersion("11.0.2").build());
    assertThat(f.isFailing()).isTrue();
    assertThat(f.fix()).contains("Upgrade to a JDK");
  }
}
