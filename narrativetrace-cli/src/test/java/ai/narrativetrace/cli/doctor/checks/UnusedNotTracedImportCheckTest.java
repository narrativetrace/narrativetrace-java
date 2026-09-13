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

class UnusedNotTracedImportCheckTest {

  private final UnusedNotTracedImportCheck check = new UnusedNotTracedImportCheck();

  @Test
  void passesWithNoImportAtAll() {
    Finding f = check.run(DoctorSnapshot.builder().build());
    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(UnusedNotTracedImportCheck.ID);
  }

  @Test
  void passesWhenTheHealthyFixtureAppliesTheAnnotation() {
    assertThat(check.run(DoctorSnapshot.healthy()).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void failsWhenImportedButNeverApplied() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putSourceFile(
                "Svc.java", "import ai.narrativetrace.api.NotTraced;\nclass Svc { String token; }")
            .build();
    Finding f = check.run(s);
    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("Svc.java");
  }

  @Test
  void listsMultipleOffendingFilesSorted() {
    String importOnly = "import ai.narrativetrace.api.NotTraced;\nclass X {}";
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putSourceFile("Z.java", importOnly)
            .putSourceFile("A.java", importOnly)
            .build();
    assertThat(check.run(s).message()).contains("A.java, Z.java");
  }
}
