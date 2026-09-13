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

class RedactionProofCheckTest {

  private final RedactionProofCheck check = new RedactionProofCheck();

  @Test
  void passesWhenTracingIsNotInUse() {
    Finding f = check.run(DoctorSnapshot.builder().build());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(RedactionProofCheck.ID);
  }

  @Test
  void passesWhenTheHealthyFixtureProvesRedaction() {
    assertThat(check.run(DoctorSnapshot.healthy()).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void passesWhenProvenViaTheProxyOrAgentModuleAlone() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .addDependencyCoordinate("ai.narrativetrace:narrativetrace-proxy:0.2.2")
            .putSourceFile("src/test/java/T.java", "assertThat(rendered).contains(\"[REDACTED]\");")
            .build();
    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsWhenTracedButNoTestProvesRedaction() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .addDependencyCoordinate("ai.narrativetrace:narrativetrace-junit5:0.2.2")
            .build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.fix()).contains("[REDACTED]");
  }

  @Test
  void failsWhenTheOnlyMatchIsNotInATestFile() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .addDependencyCoordinate("ai.narrativetrace:narrativetrace-agent:0.2.2")
            .putSourceFile("src/main/java/Doc.java", "// example: \"[REDACTED]\"")
            .build();
    assertThat(check.run(s).isFailing()).isTrue();
  }
}
