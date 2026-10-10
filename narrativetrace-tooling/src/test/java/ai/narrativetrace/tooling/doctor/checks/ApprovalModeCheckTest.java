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
package ai.narrativetrace.tooling.doctor.checks;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApprovalModeCheckTest {

  private static final String BASELINE = "src/test/narratives/OrderTest/places_order.approved.nt";

  private final ApprovalModeCheck check = new ApprovalModeCheck();

  @Test
  void failsWhenBaselinesExistButNothingComparesThem() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder().putApprovalDirFile(BASELINE, "scenario: s\n").build();

    Finding f = check.run(s);

    assertThat(f.isFailing()).isTrue();
    assertThat(f.id()).isEqualTo("config.approval-mode");
    assertThat(f.message())
        .contains(".approved.nt baselines exist but approval mode is off — nothing compares them");
    assertThat(f.fix()).contains("approval.set(true)");
  }

  @Test
  void passesWithNoBaselineAtAll() {
    Finding f = check.run(DoctorSnapshot.healthy());

    assertThat(f.status()).isEqualTo(Finding.Status.PASS);
    assertThat(f.id()).isEqualTo(ApprovalModeCheck.ID);
  }

  @Test
  void aReceivedFileAloneIsNotABaseline() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putApprovalDirFile(BASELINE.replace(".approved.nt", ".received.nt"), "scenario: s\n")
            .build();

    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativeTrace {\n    approval.set(true)\n}\n",
        "narrativeTrace {\n    approval = true\n}\n",
        "narrativeTrace {\n    approval.set( true )   // compare against the baselines\n}\n",
        "tasks.test {\n    systemProperty(\"narrativetrace.approval\", \"true\")\n}\n",
        "tasks.test {\n    jvmArgs(\"-Dnarrativetrace.approval=true\")\n}\n"
      })
  void passesWhenTheBuildTurnsApprovalModeOn(String build) {
    DoctorSnapshot s = withBaseline().buildFileContent(build).build();

    Finding f = check.run(s);

    assertThat(f.status()).as(build).isEqualTo(Finding.Status.PASS);
    assertThat(f.message()).contains("approval mode is on");
  }

  @Test
  void passesWhenAModuleManifestTurnsItOn() {
    DoctorSnapshot s =
        withBaseline()
            .putManifestFile("app/build.gradle.kts", "narrativeTrace {\n  approval.set(true)\n}\n")
            .build();

    assertThat(check.run(s).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void aGroovyModuleScriptCountsAndAVersionCatalogNeverDoes() {
    assertThat(
            check
                .run(
                    withBaseline().putManifestFile("app/build.gradle", "approval = true\n").build())
                .status())
        .isEqualTo(Finding.Status.PASS);
    assertThat(
            check
                .run(
                    withBaseline()
                        .putManifestFile("gradle/libs.versions.toml", "approval = true\n")
                        .build())
                .isFailing())
        .isTrue();
  }

  @Test
  void passesWhenASystemOrGradlePropertyTurnsItOn() {
    assertThat(
            check
                .run(withBaseline().putSystemProperty("narrativetrace.approval", "true").build())
                .status())
        .isEqualTo(Finding.Status.PASS);
    assertThat(
            check
                .run(withBaseline().putGradleProperty("narrativetrace.approval", "TRUE").build())
                .status())
        .isEqualTo(Finding.Status.PASS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativeTrace {\n    // approval.set(true)\n}\n",
        "narrativeTrace {\n    approval.set(false)\n}\n",
        "narrativeTrace {\n    approval = false\n}\n",
        "tasks.test {\n    systemProperty(\"narrativetrace.approval\", \"false\")\n}\n",
        "narrativeTrace {\n    approvedDir.set(file(\"src/test/narratives\"))\n}\n"
      })
  void failsWhenTheSwitchIsOnlyNamedOffOrCommentedOut(String build) {
    DoctorSnapshot s = withBaseline().buildFileContent(build).build();

    assertThat(check.run(s).isFailing()).as(build).isTrue();
  }

  @Test
  void aPropertySetToFalseDoesNotTurnItOn() {
    DoctorSnapshot s = withBaseline().putSystemProperty("narrativetrace.approval", "false").build();

    assertThat(check.run(s).isFailing()).isTrue();
  }

  @Test
  void theFixNamesTheSwitchBothWays() {
    Finding f = check.run(withBaseline().build());

    assertThat(f.fix()).contains("approval.set(true)").contains("narrativetrace.approval=true");
  }

  private static DoctorSnapshot.Builder withBaseline() {
    return DoctorSnapshot.healthy().toBuilder().putApprovalDirFile(BASELINE, "scenario: s\n");
  }
}
