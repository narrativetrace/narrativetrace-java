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

class AgentDepthModeCheckTest {

  private final AgentDepthModeCheck check = new AgentDepthModeCheck();

  private static DoctorSnapshot build(String content) {
    return DoctorSnapshot.healthy().toBuilder().buildFileContent(content).build();
  }

  @Test
  void warnsWhenDepthIsSetAndModeIsAbsent() {
    Finding f =
        check.run(build("narrativeTrace {\n  agent {\n    depth.set(\"internal\")\n  }\n}\n"));

    assertThat(f.status()).isEqualTo(Finding.Status.WARN);
    assertThat(f.isFailing()).isFalse();
    assertThat(f.id()).isEqualTo("config.agent-depth-mode");
    assertThat(f.message()).contains("build.gradle(.kts)").contains("no effect").contains("proxy");
    assertThat(f.fix()).contains("mode.set(\"agent\")");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativeTrace {\n  mode.set(\"proxy\")\n  agent { depth.set(\"internal\") }\n}\n",
        "narrativeTrace {\n  agent { maxInternalSpans.set(500) }\n}\n",
        "narrativeTrace {\n  agent {\n    depth = \"internal\"\n  }\n}\n"
      })
  void warnsForDepthOrMaxInternalSpansUnderProxy(String build) {
    assertThat(check.run(build(build)).status()).as(build).isEqualTo(Finding.Status.WARN);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativeTrace {\n  mode.set(\"agent\")\n  agent { depth.set(\"internal\") }\n}\n",
        "narrativeTrace {\n  mode = \"agent\"\n  agent { depth.set(\"internal\") }\n}\n"
      })
  void passesWhenModeIsAgent(String build) {
    assertThat(check.run(build(build)).status()).as(build).isEqualTo(Finding.Status.PASS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativeTrace {\n  agent { packages.set(listOf(\"com.example\")) }\n}\n",
        "narrativeTrace {\n  scope.set(\"test\")\n}\n",
        "narrativeTrace {\n  agent {\n    // depth.set(\"internal\")\n  }\n}\n",
        "val depth = 3\n"
      })
  void passesWhenNoDepthIsSet(String build) {
    assertThat(check.run(build(build)).status()).as(build).isEqualTo(Finding.Status.PASS);
  }

  @Test
  void namesAModuleFileWhenTheSettingLivesThere() {
    DoctorSnapshot s =
        DoctorSnapshot.healthy().toBuilder()
            .putManifestFile(
                "app/build.gradle.kts", "narrativeTrace { agent { depth.set(\"internal\") } }\n")
            .build();

    Finding f = check.run(s);

    assertThat(f.status()).isEqualTo(Finding.Status.WARN);
    assertThat(f.message()).contains("app/build.gradle.kts");
  }
}
