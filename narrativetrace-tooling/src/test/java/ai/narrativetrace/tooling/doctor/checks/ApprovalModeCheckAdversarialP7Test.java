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

/** Commented-out, mis-cased and near-miss switch spellings for {@link ApprovalModeCheck}. */
class ApprovalModeCheckAdversarialP7Test {

  private static final String BASELINE = "src/test/narratives/OrderTest/places_order.approved.nt";

  private final ApprovalModeCheck check = new ApprovalModeCheck();

  @Test
  void aSwitchInsideAMultiLineBlockCommentDoesNotCountAsOn() {
    // SUSPECTED BUG: only lines starting with * are treated as comment, so the body of a block
    // comment that does not repeat the star reads as a live switch.
    var build = "narrativeTrace {\n    /*\n    approval.set(true)\n    */\n}\n";

    assertThat(check.run(withBaseline().buildFileContent(build).build()).isFailing()).isTrue();
  }

  @Test
  void aSwitchInsideAnInlineBlockCommentDoesNotCountAsOn() {
    // SUSPECTED BUG: the line is not comment-only, so the whole line is kept and the commented
    // switch is matched as if it were live.
    var build = "narrativeTrace { /* approval.set(true) */ }\n";

    assertThat(check.run(withBaseline().buildFileContent(build).build()).isFailing()).isTrue();
  }

  @Test
  void aJvmArgumentWithUpperCaseTrueTurnsApprovalModeOn() {
    // SUSPECTED BUG: the runtime reads this property with equalsIgnoreCase, so the test JVM is
    // in approval mode, but the manifest match is case-sensitive and reports it as off.
    var build = "tasks.test {\n    jvmArgs(\"-Dnarrativetrace.approval=TRUE\")\n}\n";

    assertThat(check.run(withBaseline().buildFileContent(build).build()).status())
        .isEqualTo(Finding.Status.PASS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"approval = trueish", "preapproval = true"})
  void aNearMissSpellingOfTheSwitchDoesNotCountAsOn(String line) {
    // SUSPECTED BUG: the compact manifest match is a substring test, so a longer value or a
    // longer key that ends in the switch name passes as approval mode on.
    var build = "narrativeTrace {\n    " + line + "\n}\n";

    assertThat(check.run(withBaseline().buildFileContent(build).build()).isFailing())
        .as(line)
        .isTrue();
  }

  @Test
  void aPaddedTrueSystemPropertyTurnsApprovalModeOn() {
    var snapshot = withBaseline().putSystemProperty("narrativetrace.approval", " TRUE ").build();

    assertThat(check.run(snapshot).status()).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void aModuleSwitchOnTurnsItOnEvenWhenTheRootBuildSaysOff() {
    var snapshot =
        withBaseline()
            .buildFileContent("narrativeTrace {\n    approval.set(false)\n}\n")
            .putManifestFile("app/build.gradle.kts", "narrativeTrace {\n  approval.set(true)\n}\n")
            .build();

    assertThat(check.run(snapshot).status()).isEqualTo(Finding.Status.PASS);
  }

  private static DoctorSnapshot.Builder withBaseline() {
    return DoctorSnapshot.healthy().toBuilder().putApprovalDirFile(BASELINE, "scenario: s\n");
  }
}
