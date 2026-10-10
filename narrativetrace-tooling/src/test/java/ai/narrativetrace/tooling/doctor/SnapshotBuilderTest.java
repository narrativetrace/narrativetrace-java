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
package ai.narrativetrace.tooling.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.Carriers;
import ai.narrativetrace.tooling.init.InstalledSkill;
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

  /**
   * The doctor's own report is not rendered trace output, and reading it as such made the doctor
   * fail on its own text: every report carries {@code arg0} inside the very messages that talk
   * about {@code arg0}-style parameter names, so a SECOND run reported the first run's report as a
   * project defect. Found by a Tier B trial, on a project that had done nothing wrong but run the
   * doctor twice — which is exactly what the published prompt tells a reader to do.
   */
  @Test
  void neverReadsTheDoctorsOwnReportAsRenderedOutput() throws IOException {
    Path output = project.resolve("build/narrativetrace");
    Files.createDirectories(output);
    Files.writeString(
        output.resolve("doctor-report.json"),
        "{\"findings\": [{\"message\": \"No rendered output shows arg0-style placeholders\"}]}");
    Files.writeString(output.resolve("trace.nt"), "placeOrder(customerId: \"C-1\")");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.outputFiles()).containsOnlyKeys("build/narrativetrace/trace.nt");
  }

  /** The NAME is what identifies it, in either output root this walk recognises. */
  @Test
  void skipsTheDoctorsOwnReportInEitherOutputRoot() throws IOException {
    Path output = project.resolve("narrativetrace-output");
    Files.createDirectories(output);
    Files.writeString(output.resolve("doctor-report.json"), "arg0 arg1");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.outputFiles()).isEmpty();
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
  void detectsAutodetectionEnabledViaJunitPlatformPropertiesFile() throws IOException {
    Path resources = project.resolve("src/test/resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("junit-platform.properties"),
        "junit.jupiter.extensions.autodetection.enabled=true\n");

    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.extensionRegisteredViaServiceLoader()).isTrue();
  }

  @Test
  void autodetectionDisabledInJunitPlatformPropertiesIsNotRegistration() throws IOException {
    Path resources = project.resolve("src/test/resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("junit-platform.properties"),
        "junit.jupiter.extensions.autodetection.enabled=false\n");

    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.extensionRegisteredViaServiceLoader()).isFalse();
  }

  @Test
  void detectsAutodetectionEnabledViaGradleTestSystemProperty() throws IOException {
    Files.writeString(
        project.resolve("build.gradle.kts"),
        """
        tasks.test {
            systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
        }
        """);

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
    DoctorSnapshot capped = SnapshotBuilder.build(project, null, 2);
    assertThat(capped.sourceFiles().size()).isLessThanOrEqualTo(2);
  }

  // --- the agent skills the twelfth check reads --------------------------------------------

  /** A page of ours, exactly as {@code init} stamps it. */
  private void writeInstalledSkill(String name, String coordinate) throws IOException {
    Path page = project.resolve(".agents/skills/" + name + "/SKILL.md");
    Files.createDirectories(page.getParent());
    Files.writeString(
        page,
        "---\nname: "
            + name
            + "\n---\n\n<!-- installed by narrativetrace init from "
            + coordinate
            + " \u2014 edit the catalogue, not this file -->\n");
  }

  @Test
  void readsInstalledSkillsThroughTheInstallersOwnReader() throws IOException {
    writeInstalledSkill("doctor", "ai.narrativetrace:narrativetrace-skills:1.2.3");
    Files.createDirectories(project.resolve(".agents/skills/somebody-else"));
    Files.writeString(
        project.resolve(".agents/skills/somebody-else/SKILL.md"), "---\nname: theirs\n---\n");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.installedSkills())
        .extracting(InstalledSkill::name, InstalledSkill::presence, InstalledSkill::coordinate)
        .containsExactlyInAnyOrder(
            tuple(
                "doctor",
                InstalledSkill.Presence.OURS,
                "ai.narrativetrace:narrativetrace-skills:1.2.3"),
            tuple("somebody-else", InstalledSkill.Presence.FOREIGN, ""));
  }

  @Test
  void withNoCarrierGivenTheSnapshotSaysItCannotTell() {
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.carrierResolved()).isFalse();
    assertThat(s.carrierCoordinate()).isEmpty();
    assertThat(s.catalogueSkills()).isEmpty();
  }

  @Test
  void withACarrierGivenTheSnapshotCarriesItsCoordinateAndCatalogue(@TempDir Path carrierParent) {
    Carrier carrier = Carriers.fake(carrierParent, "doctor", "clarity");

    DoctorSnapshot s = SnapshotBuilder.build(project, carrier);

    assertThat(s.carrierCoordinate()).isEqualTo(Carriers.FAKE_COORDINATE);
    assertThat(s.catalogueSkills()).containsExactly("doctor", "clarity");
    assertThat(s.carrierResolved()).isTrue();
  }

  @Test
  void aNullCarrierIsTheSameAsNoneGiven(@TempDir Path unused) {
    assertThat(SnapshotBuilder.build(project, null).carrierResolved()).isFalse();
  }

  /**
   * The doctor never crashes on a project it cannot read. The installer's reader is deliberately
   * NOT best-effort — "unreadable" and "absent" lead to opposite install plans — so the doctor
   * absorbs its refusal here and reports no skills rather than propagating it.
   */
  @Test
  void aProjectRootThatIsNotADirectoryLeavesTheSkillsEmpty() {
    DoctorSnapshot s = SnapshotBuilder.build(project.resolve("does-not-exist"));
    assertThat(s.installedSkills()).isEmpty();
  }

  /**
   * The running JVM's own properties reach the snapshot — the checks read them for configuration.
   */
  @Test
  void theRunningJvmsSystemPropertiesAreCaptured() {
    assertThat(SnapshotBuilder.build(project).systemProperties()).containsKey("java.home");
  }

  /** A project root that is not there registers nothing — not even by accident. */
  @Test
  void aMissingProjectDirectoryRegistersNoExtension() {
    DoctorSnapshot s = SnapshotBuilder.build(project.resolve("does-not-exist"));
    assertThat(s.extensionRegistered()).isFalse();
    assertThat(s.extensionRegisteredViaServiceLoader()).isFalse();
    assertThat(s.extensionRegisteredViaExtendWith()).isFalse();
  }

  /** The launcher rule needs BOTH words on one line: either alone is somebody else's dependency. */
  @Test
  void aTestRuntimeOnlyThatIsNotTheLauncherIsNotTheLauncher() throws IOException {
    Files.writeString(
        project.resolve("build.gradle.kts"),
        """
        dependencies {
            testRuntimeOnly("com.example:something-else:1.0")
            implementation("org.junit.platform:junit-platform-launcher:1.11.4")
        }
        """);

    assertThat(SnapshotBuilder.build(project).launcherOnTestRuntimeOnly()).isFalse();
  }

  /**
   * Framework markers live where the module that uses the framework lives: a module's own build
   * file, the settings file's plugin management, the version catalog. All of them are manifests; a
   * copy of one under a {@code build/} directory is build output and is not.
   */
  @Test
  void collectsEveryManifestButNotBuildOutput() throws IOException {
    write("app/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }");
    write("settings.gradle.kts", "include(\"app\")");
    write("gradle/libs.versions.toml", "[libraries]");
    write("lib/build.gradle", "apply plugin: 'java'");
    write("build/tmp/copy/build.gradle.kts", "copied");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.manifestFiles())
        .containsOnlyKeys(
            "app/build.gradle.kts",
            "settings.gradle.kts",
            "gradle/libs.versions.toml",
            "lib/build.gradle");
    assertThat(s.manifestText()).contains("org.springframework.boot");
  }

  /**
   * Wiring lives in Kotlin and Groovy sources and in configuration files as often as in Java: all
   * of them under {@code src/} are resource files for the framework checks, and none is a Java
   * source the older checks read.
   */
  @Test
  void collectsNonJavaSourcesAndConfigurationUnderSrc() throws IOException {
    write("src/main/kotlin/com/acme/TraceConfig.kt", "@EnableNarrativeTrace class T");
    write("src/main/groovy/com/acme/Setup.groovy", "class Setup {}");
    write("src/main/resources/application.yml", "narrativetrace:\n  base-packages: com.acme\n");
    write("src/main/resources/application.properties", "a=b");
    write("src/main/webapp/WEB-INF/web.xml", "<web-app/>");
    write("src/main/resources/logo.png", "not text we read");
    write("app/build/generated/src/main/resources/application.yml", "generated");

    DoctorSnapshot s = SnapshotBuilder.build(project);

    assertThat(s.resourceFiles())
        .containsOnlyKeys(
            "src/main/kotlin/com/acme/TraceConfig.kt",
            "src/main/groovy/com/acme/Setup.groovy",
            "src/main/resources/application.yml",
            "src/main/resources/application.properties",
            "src/main/webapp/WEB-INF/web.xml");
    assertThat(s.sourceFiles()).isEmpty();
  }

  /** "src" as a substring of a directory name is not a source directory. */
  @Test
  void aDirectoryThatOnlyEndsInSrcIsNotASourceRoot() throws IOException {
    write("websrc/main/resources/application.yml", "x: y");
    assertThat(SnapshotBuilder.build(project).resourceFiles()).isEmpty();
  }

  /**
   * The Java bucket matches a {@code src} DIRECTORY too, never a name that merely ends in it: a
   * test class under {@code mysrc/} registering the extension is not this project's registration.
   */
  @Test
  void aJavaFileUnderADirectoryThatOnlyEndsInSrcIsNotASource() throws IOException {
    write(
        "mysrc/test/java/OrderTest.java",
        "@ExtendWith(NarrativeTraceExtension.class) class OrderTest {}");
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.sourceFiles()).isEmpty();
    assertThat(s.extensionRegisteredViaExtendWith()).isFalse();
  }

  /**
   * A directory named {@code build} INSIDE a source tree is source; only one above it is output.
   */
  @Test
  void aBuildDirectoryInsideSrcIsSourceNotBuildOutput() throws IOException {
    write("src/main/resources/build/application.yml", "narrativetrace:\n  base-packages: a\n");
    write("build/generated/src/main/resources/application.yml", "generated");
    assertThat(SnapshotBuilder.build(project).resourceFiles())
        .containsOnlyKeys("src/main/resources/build/application.yml");
  }

  /** A module's own src directory is a source tree as much as the root's. */
  @Test
  void aModulesNestedSrcDirectoryHoldsResourcesAndSources() throws IOException {
    write("app/src/main/resources/application.yml", "a: b");
    write("app/src/main/java/com/acme/A.java", "class A {}");
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.resourceFiles()).containsOnlyKeys("app/src/main/resources/application.yml");
    assertThat(s.sourceFiles()).containsOnlyKeys("app/src/main/java/com/acme/A.java");
  }

  private void write(String relative, String content) throws IOException {
    Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
