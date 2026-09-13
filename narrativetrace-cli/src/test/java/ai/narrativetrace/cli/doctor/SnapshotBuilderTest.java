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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotBuilderTest {

  @TempDir Path project;

  @Test
  void onAMissingProjectDirectoryEverythingStaysEmpty() {
    DoctorSnapshot s = SnapshotBuilder.build(project.resolve("does-not-exist"));
    assertThat(s.buildFileContent()).isEmpty();
    assertThat(s.sourceFiles()).isEmpty();
    assertThat(s.declaredDependencyCoordinates()).isEmpty();
  }

  @Test
  void readsABuildGradleKtsFileAndItsDependencyCoordinates() throws IOException {
    Files.writeString(
        project.resolve("build.gradle.kts"),
        """
        dependencies {
            api("ai.narrativetrace:narrativetrace-junit5:0.2.2")
            testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
        }
        tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
        """);

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.buildFileContent()).contains("narrativetrace-junit5");
    assertThat(s.declaredDependencyCoordinates())
        .contains(
            "ai.narrativetrace:narrativetrace-junit5:0.2.2",
            "org.junit.jupiter:junit-jupiter:5.11.4",
            "org.junit.platform:junit-platform-launcher:1.11.4");
    assertThat(s.launcherOnTestRuntimeOnly()).isTrue();
    assertThat(s.compilerArgsDeclareParameters()).isTrue();
  }

  @Test
  void fallsBackToAPlainBuildGradleWhenNoKtsFileExists() throws IOException {
    Files.writeString(project.resolve("build.gradle"), "dependencies { }");
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.buildFileContent()).isEqualTo("dependencies { }");
  }

  @Test
  void readsGradleProperties() throws IOException {
    Files.writeString(
        project.resolve("gradle.properties"),
        """
        # a comment
        ! also a comment
        narrativetrace.output=false

        malformedLineWithNoEquals
        version = 1.2.3
        """);
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.gradleProperties()).containsEntry("narrativetrace.output", "false");
    assertThat(s.gradleProperties()).containsEntry("version", "1.2.3");
    assertThat(s.gradleProperties()).doesNotContainKey("malformedLineWithNoEquals");
  }

  @Test
  void scansSourceFilesUnderSrcAndDetectsExtendWith() throws IOException {
    Path testDir = project.resolve("src/test/java/com/example");
    Files.createDirectories(testDir);
    Files.writeString(
        testDir.resolve("SvcTest.java"),
        "@ExtendWith(NarrativeTraceExtension.class)\nclass SvcTest {}");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.sourceFiles()).containsKey("src/test/java/com/example/SvcTest.java");
    assertThat(s.extensionRegisteredViaExtendWith()).isTrue();
  }

  @Test
  void ignoresNonJavaFilesAndFilesOutsideSrc() throws IOException {
    Files.writeString(project.resolve("README.md"), "not java");
    Path outside = project.resolve("scripts");
    Files.createDirectories(outside);
    Files.writeString(outside.resolve("Tool.java"), "class Tool {}");

    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.sourceFiles()).isEmpty();
  }

  @Test
  void capturesOutputAndApprovalFiles() throws IOException {
    Path output = project.resolve("build/narrativetrace");
    Files.createDirectories(output);
    Files.writeString(output.resolve("trace.nt"), "placeOrder(arg0)");

    Path narratives = project.resolve("src/test/narratives/Svc");
    Files.createDirectories(narratives);
    Files.writeString(narratives.resolve("scenario.approved.nt"), "approved");
    Files.writeString(narratives.resolve("scenario.received.nt"), "received");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.outputFiles()).containsEntry("build/narrativetrace/trace.nt", "placeOrder(arg0)");
    assertThat(s.approvalDirFiles())
        .containsKeys(
            "src/test/narratives/Svc/scenario.approved.nt",
            "src/test/narratives/Svc/scenario.received.nt");
  }

  @Test
  void excludesVcsAndDependencyCacheDirectories() throws IOException {
    Path git = project.resolve(".git/src");
    Files.createDirectories(git);
    Files.writeString(git.resolve("Should.java"), "class Should {}");

    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.sourceFiles()).isEmpty();
  }

  @Test
  void detectsTheServiceLoaderRegistration() throws IOException {
    Path services = project.resolve("src/test/resources/META-INF/services");
    Files.createDirectories(services);
    Files.writeString(
        services.resolve("org.junit.jupiter.api.extension.Extension"),
        "ai.narrativetrace.junit5.NarrativeTraceExtension\n");

    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.extensionRegisteredViaServiceLoader()).isTrue();
  }

  @Test
  void aFileNamedForAnOptionalArtifactThatIsActuallyADirectoryIsSkippedNotThrown()
      throws IOException {
    // build.gradle.kts as a directory: Files.readString on it always fails, exercising the
    // "unreadable, fall back to empty" branch without relying on OS file permissions.
    Files.createDirectories(project.resolve("build.gradle.kts"));
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.buildFileContent()).isEmpty();
  }

  @Test
  void aBoundedWalkStopsAtTheFileCap() throws IOException {
    Path srcDir = project.resolve("src/main/java");
    Files.createDirectories(srcDir);
    for (int i = 0; i < 5; i++) {
      Files.writeString(srcDir.resolve("Class" + i + ".java"), "class Class" + i + " {}");
    }
    DoctorSnapshot capped = SnapshotBuilder.build(project, 2);
    assertThat(capped.sourceFiles().size()).isLessThanOrEqualTo(2);
  }
}
