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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  // --- the agent skills the twelfth check reads --------------------------------------------

  @Test
  void anEmptySnapshotKnowsOfNoSkillsAndNoCarrier() {
    DoctorSnapshot s = DoctorSnapshot.builder().build();
    assertThat(s.installedSkills()).isEmpty();
    assertThat(s.carrierCoordinate()).isEmpty();
    assertThat(s.catalogueSkills()).isEmpty();
    assertThat(s.carrierResolved()).isFalse();
  }

  @Test
  void healthyCarriesAResolvedCarrierWithEverySkillInstalledFromIt() {
    DoctorSnapshot s = DoctorSnapshot.healthy();
    assertThat(s.carrierResolved()).isTrue();
    assertThat(s.catalogueSkills()).isNotEmpty();
    assertThat(s.installedSkills())
        .isNotEmpty()
        .allMatch(skill -> skill.presence() == InstalledSkill.Presence.OURS)
        .allMatch(skill -> skill.coordinate().equals(s.carrierCoordinate()));
  }

  @Test
  void theBuilderTakesTheSkillsAndTheCarrier() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .addInstalledSkill(ours("narrativetrace-doctor", "ai.narrativetrace:x:1"))
            .carrier("ai.narrativetrace:x:1", List.of("narrativetrace-doctor"))
            .build();

    assertThat(s.carrierCoordinate()).isEqualTo("ai.narrativetrace:x:1");
    assertThat(s.catalogueSkills()).containsExactly("narrativetrace-doctor");
    assertThat(s.installedSkills())
        .extracting(InstalledSkill::name)
        .containsExactly("narrativetrace-doctor");
  }

  /**
   * A coordinate with no catalogue, or a catalogue with no coordinate, is a carrier that is neither
   * resolved nor unresolved — and the twelfth check's "I cannot tell" answer keys on exactly that
   * distinction, so the half-state is refused where it would be built rather than interpreted.
   */
  @Test
  void theBuilderRefusesAHalfResolvedCarrier() {
    assertThatThrownBy(() -> DoctorSnapshot.builder().carrier("ai.narrativetrace:x:1", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DoctorSnapshot.builder().carrier("", List.of("doctor")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DoctorSnapshot.builder().carrier(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DoctorSnapshot.builder().carrier("", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anUnresolvedCarrierIsTheBuilderDefaultAndMayBeSaidOutLoud() {
    DoctorSnapshot s = DoctorSnapshot.builder().carrier("", List.of()).build();
    assertThat(s.carrierResolved()).isFalse();
  }

  /** "Unresolved" has one representation, so a blank coordinate cannot read as a resolved one. */
  @Test
  void aWhitespaceCoordinateIsStoredAsUnresolved() {
    DoctorSnapshot s = DoctorSnapshot.builder().carrier("   ", List.of()).build();
    assertThat(s.carrierCoordinate()).isEmpty();
    assertThat(s.carrierResolved()).isFalse();
  }

  @Test
  void theSnapshotFindsOneSkillDirectoryByFlavourAndName() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .addInstalledSkill(ours("narrativetrace-doctor", "ai.narrativetrace:x:1"))
            .build();
    assertThat(s.installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")).isPresent();
    assertThat(s.installedSkill(SkillFlavour.CLAUDE, "narrativetrace-doctor")).isEmpty();
    assertThat(s.installedSkill(SkillFlavour.AGENTS, "other")).isEmpty();
  }

  @Test
  void theBuilderRefusesANullInstalledSkill() {
    assertThatThrownBy(() -> DoctorSnapshot.builder().addInstalledSkill(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toBuilderRoundTripsTheSkillsAndTheCarrier() {
    DoctorSnapshot original = DoctorSnapshot.healthy();
    DoctorSnapshot copy = original.toBuilder().runningJavaVersion("11.0.0").build();

    assertThat(copy.installedSkills()).isEqualTo(original.installedSkills());
    assertThat(copy.carrierCoordinate()).isEqualTo(original.carrierCoordinate());
    assertThat(copy.catalogueSkills()).isEqualTo(original.catalogueSkills());
  }

  private static InstalledSkill ours(String name, String coordinate) {
    return new InstalledSkill(
        SkillFlavour.AGENTS, name, InstalledSkill.Presence.OURS, coordinate, "page");
  }

  /** Calling carrier() twice REPLACES the catalogue; appending would invent skills nobody ships. */
  @Test
  void asecondCarrierCallReplacesTheFirstCatalogue() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .carrier("ai.narrativetrace:x:1", List.of("first"))
            .carrier("ai.narrativetrace:x:2", List.of("second"))
            .build();

    assertThat(s.carrierCoordinate()).isEqualTo("ai.narrativetrace:x:2");
    assertThat(s.catalogueSkills()).containsExactly("second");
  }

  /** Every map a snapshot carries survives a toBuilder() copy, not just the ones a check reads. */
  @Test
  void toBuilderRoundTripsEveryMapItCarries() {
    DoctorSnapshot original =
        DoctorSnapshot.healthy().toBuilder()
            .putEnv("NARRATIVETRACE_OUTPUT", "true")
            .putSystemProperty("narrativetrace.output", "true")
            .putGradleProperty("narrativetrace.outputDir", "out")
            .putOutputFile("build/narrativetrace/a.md", "rendered")
            .putApprovalDirFile("src/test/narratives/a.approved.nt", "baseline")
            .build();

    DoctorSnapshot copy = original.toBuilder().build();

    assertThat(copy.env()).isEqualTo(original.env());
    assertThat(copy.systemProperties()).isEqualTo(original.systemProperties());
    assertThat(copy.gradleProperties()).isEqualTo(original.gradleProperties());
    assertThat(copy.outputFiles()).isEqualTo(original.outputFiles());
    assertThat(copy.approvalDirFiles()).isEqualTo(original.approvalDirFiles());
  }

  /** A commented-out dependency is not a dependency, in Kotlin/Groovy DSL or a TOML catalog. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "// implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.2\")",
        "  # spring = \"ai.narrativetrace:narrativetrace-spring:0.2.2\"",
        "/* implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.2\")",
        " * implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.2\")",
      })
  void aCommentOnlyLineIsNotPartOfTheManifestText(String line) {
    DoctorSnapshot s = DoctorSnapshot.builder().buildFileContent(line).build();
    assertThat(s.manifestText()).doesNotContain("narrativetrace-spring");
  }

  /**
   * A version catalog may spell a library as {@code group}/{@code name} instead of one {@code
   * module} string; the manifest text carries it as the quoted coordinate the markers match.
   */
  @Test
  void aCatalogGroupAndNameEntryReadsAsItsCoordinate() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putManifestFile(
                "gradle/libs.versions.toml",
                "spring-context = { group = \"org.springframework\", name = \"spring-context\","
                    + " version = \"6.2.19\" }\n")
            .build();
    assertThat(s.manifestText()).contains("\"org.springframework:spring-context\"");
  }

  @Test
  void theNarrativeTraceVersionIsReadFromACatalogPluginEntry() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .putManifestFile(
                "gradle/libs.versions.toml",
                "[plugins]\nnarrativetrace = { id = \"ai.narrativetrace\", version = \"0.2.2\" }\n")
            .build();
    assertThat(s.narrativeTraceVersion()).contains("0.2.2");
  }

  @Test
  void toBuilderKeepsTheManifestAndResourceFiles() {
    DoctorSnapshot original =
        DoctorSnapshot.builder()
            .putManifestFile("app/build.gradle.kts", "m")
            .putResourceFile("src/main/resources/application.yml", "r")
            .build();
    DoctorSnapshot copy = original.toBuilder().build();
    assertThat(copy.manifestFiles()).isEqualTo(original.manifestFiles());
    assertThat(copy.resourceFiles()).isEqualTo(original.resourceFiles());
  }
}
