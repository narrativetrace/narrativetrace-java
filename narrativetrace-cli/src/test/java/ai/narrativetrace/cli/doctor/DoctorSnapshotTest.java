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

import org.junit.jupiter.api.Test;

class DoctorSnapshotTest {

  @Test
  void healthyIsFullyWired() {
    DoctorSnapshot s = DoctorSnapshot.healthy();
    assertThat(s.declaresDependency("ai.narrativetrace:narrativetrace-junit5")).isTrue();
    assertThat(s.declaresDependency("com.example:nonexistent")).isFalse();
    assertThat(s.extensionRegistered()).isTrue();
    assertThat(s.launcherOnTestRuntimeOnly()).isTrue();
    assertThat(s.compilerArgsDeclareParameters()).isTrue();
    assertThat(s.buildFileContent()).contains("narrativetrace-junit5");
  }

  @Test
  void anEmptySnapshotDeclaresNothing() {
    DoctorSnapshot s = DoctorSnapshot.builder().build();
    assertThat(s.extensionRegistered()).isFalse();
    assertThat(s.declaresDependency("anything")).isFalse();
    assertThat(s.env()).isEmpty();
    assertThat(s.systemProperties()).isEmpty();
    assertThat(s.gradleProperties()).isEmpty();
    assertThat(s.sourceFiles()).isEmpty();
    assertThat(s.outputFiles()).isEmpty();
    assertThat(s.approvalDirFiles()).isEmpty();
  }

  @Test
  void builderCoversEveryField() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .runningJavaVersion("21.0.1")
            .putEnv("NARRATIVETRACE_OUTPUT", "false")
            .putSystemProperty("narrativetrace.output", "maybe")
            .putGradleProperty("narrativetrace.output", "true")
            .buildFileContent("plugins {}")
            .addDependencyCoordinate("a:b:1.0")
            .clearDependencyCoordinates()
            .addDependencyCoordinate("c:d:2.0")
            .launcherOnTestRuntimeOnly(true)
            .compilerArgsDeclareParameters(true)
            .putSourceFile("A.java", "class A {}")
            .clearSourceFiles()
            .putSourceFile("B.java", "class B {}")
            .putOutputFile("out.nt", "content")
            .putApprovalDirFile("scenario.received.nt", "content")
            .extensionRegisteredViaServiceLoader(true)
            .extensionRegisteredViaExtendWith(false)
            .build();

    assertThat(s.runningJavaVersion()).isEqualTo("21.0.1");
    assertThat(s.env()).containsEntry("NARRATIVETRACE_OUTPUT", "false");
    assertThat(s.systemProperties()).containsEntry("narrativetrace.output", "maybe");
    assertThat(s.gradleProperties()).containsEntry("narrativetrace.output", "true");
    assertThat(s.declaredDependencyCoordinates()).containsExactly("c:d:2.0");
    assertThat(s.sourceFiles()).containsOnlyKeys("B.java");
    assertThat(s.outputFiles()).containsEntry("out.nt", "content");
    assertThat(s.approvalDirFiles()).containsKey("scenario.received.nt");
    assertThat(s.extensionRegisteredViaServiceLoader()).isTrue();
    assertThat(s.extensionRegisteredViaExtendWith()).isFalse();
    assertThat(s.extensionRegistered()).isTrue();
  }

  @Test
  void toBuilderRoundTripsEveryField() {
    DoctorSnapshot original = DoctorSnapshot.healthy();
    DoctorSnapshot copy = original.toBuilder().runningJavaVersion("11.0.0").build();

    assertThat(copy.runningJavaVersion()).isEqualTo("11.0.0");
    assertThat(copy.buildFileContent()).isEqualTo(original.buildFileContent());
    assertThat(copy.declaredDependencyCoordinates())
        .isEqualTo(original.declaredDependencyCoordinates());
    assertThat(copy.launcherOnTestRuntimeOnly()).isEqualTo(original.launcherOnTestRuntimeOnly());
    assertThat(copy.compilerArgsDeclareParameters())
        .isEqualTo(original.compilerArgsDeclareParameters());
    assertThat(copy.sourceFiles()).isEqualTo(original.sourceFiles());
    assertThat(copy.extensionRegisteredViaExtendWith())
        .isEqualTo(original.extensionRegisteredViaExtendWith());
    assertThat(copy.extensionRegisteredViaServiceLoader())
        .isEqualTo(original.extensionRegisteredViaServiceLoader());
  }
}
