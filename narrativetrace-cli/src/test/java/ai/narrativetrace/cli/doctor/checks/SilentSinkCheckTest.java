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

class SilentSinkCheckTest {

  private final SilentSinkCheck check = new SilentSinkCheck();

  @Test
  void passesWhenNotYetTraced() {
    Finding f = check.run(DoctorSnapshot.builder().build());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(SilentSinkCheck.ID);
  }

  @Test
  void passesWhenOutputIsOnByDefault() {
    assertThat(check.run(DoctorSnapshot.healthy()).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void passesWhenOutputIsOffButAnAlternativeSinkIsDeclared() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putGradleProperty("narrativetrace.output", "false")
            .addDependencyCoordinate("ai.narrativetrace:narrativetrace-slf4j:0.2.2")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void passesWhenOutputIsOffButACustomEventStoreIsInSource() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putGradleProperty("narrativetrace.output", "false")
            .putSourceFile("Sink.java", "new BufferedEventConsumer(store)")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsWhenTracedAndOutputIsOffWithNoAlternative() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putGradleProperty("narrativetrace.output", "false")
            .build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.fix()).contains("narrativetrace-slf4j");
  }

  @Test
  void failsWhenTheSystemPropertyTurnsOutputOff() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putSystemProperty("narrativetrace.output", "false")
            .build();
    assertThat(check.run(s).isFailing()).isTrue();
  }
}
