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
package ai.narrativetrace.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import ai.narrativetrace.tooling.frameworks.IntegrationModule;
import ai.narrativetrace.tooling.frameworks.Wiring;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The adversarial pass over Phase 6 milestone 1 — the framework table, its wiring evidence and the
 * doctor's manifest and source scanning — kept in the PARENT package so only the public surface is
 * reachable, like {@code AdversarialM5Test}.
 *
 * <p>Every case here survived being read. Of 45 proposed, 14 failed and twelve of those were real
 * defects, now fixed with their cases moved next to the code they pin (the YAML evidence, the
 * catalog forms, comment-only manifest lines, unknown deferrals, the silent-sink plugin form, the
 * src/build segments). Two were not adopted: a coordinate inside an arbitrary string needs a Gradle
 * parser, and the silent-sink check's EventStore substring heuristic predates this milestone. What
 * is left are combinations and near misses no other test exercises, each passing.
 */
class AdversarialPhase6Test {

  private static FrameworkRow row(String id) {
    return FrameworkTable.row(id).orElseThrow();
  }

  private static boolean wiredIn(String rowId, String text) {
    return ((Wiring.Snippet) row(rowId).wiring()).appliedIn(Stream.of(text));
  }

  private static void write(Path project, String relative, String content) throws IOException {
    Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  // ---- Evidence.YamlKey and micronaut wiring ---------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "narrativetrace:\r\n  base-packages: [com.example]\r\n",
        "narrativetrace:\n\tbase-packages:\tcom.example\n",
        "narrativetrace:\n  base-packages: [a, b] # two packages\n",
        "narrativetrace:\n  base-packages:\n    - com.example\n"
      })
  void everyWellFormedYamlSpellingOfTheKeyIsTheWiring(String yaml) {
    assertThat(wiredIn("micronaut", yaml)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "# narrativetrace.base-packages=com.example",
        "my.narrativetrace.base-packages=com.example",
        "narrativetrace.base-packages-old=com.example",
        "narrativetrace.base-packages=",
        "narrativetrace.base-packages=[ ]"
      })
  void aPropertiesNearMissIsNotTheMicronautWiring(String text) {
    assertThat(wiredIn("micronaut", text)).isFalse();
  }

  // ---- Marker detection and catalogs ----------------------------------------------------

  /** End to end through the snapshot, which turns the catalog's map form into the coordinate. */
  @Test
  void aVersionCatalogGroupNameEntryIsDetectedAsSpring() {
    String catalog =
        "[libraries]\n"
            + "spring-context = { group = \"org.springframework\", name = \"spring-context\", "
            + "version = \"6.2.19\" }\n";
    DoctorSnapshot s =
        DoctorSnapshot.builder().putManifestFile("gradle/libs.versions.toml", catalog).build();
    assertThat(FrameworkTable.detected(row("spring"), s.manifestText())).isTrue();
  }

  @Test
  void aVersionCatalogPluginEntryIsDetectedAsSpring() {
    String catalog =
        "[plugins]\nspringBoot = { id = \"org.springframework.boot\", version = \"3.4.0\" }\n";
    assertThat(FrameworkTable.detected(row("spring"), catalog)).isTrue();
  }

  /** A module commented out of the build is not referenced: the doctor still says "add it". */
  @Test
  void aCommentedOutModuleIsNotAReference() {
    DoctorSnapshot s =
        DoctorSnapshot.builder()
            .buildFileContent(
                "plugins { id(\"org.springframework.boot\") }\n"
                    + "// implementation(\"ai.narrativetrace:narrativetrace-spring:0.2.2\")\n")
            .build();
    assertThat(row("spring").module().referencedIn(s.manifestText())).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "springWeb.set(true)",
        "springWeb.set( true )",
        "springWeb = true",
        "springWeb.set(\n    true\n  )",
        "narrativeTrace { modules { springWeb.set(true) } }"
      })
  void aSpringWebSettingTrueInEitherDslReferencesTheModule(String line) {
    assertThat(row("spring-web").module().referencedIn(line)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "springWeb.set(false)",
        "springWeb = false",
        "mySpringWeb.set(true)",
        "xspringWeb.set(true)",
        "springWebX.set(true)"
      })
  void aSpringWebSettingThatIsFalseOrNamesAnotherPropertyIsNotAReference(String line) {
    assertThat(row("spring-web").module().referencedIn(line)).isFalse();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "servlet|\"jakarta.servlet:jakarta.servlet-api-compat:6.0\"",
        "junit4|\"junit:junit-dep:4.13\"",
        "micrometer|\"io.micrometer:context-propagation-x:1.0\""
      })
  void aSharedPrefixArtifactIsNotTheRowsMarker(String rowId, String manifest) {
    assertThat(FrameworkTable.detected(row(rowId), manifest)).isFalse();
  }

  // ---- Fix-line rendering: modules with and without plugin / configuration ----------------

  @Test
  void theDefaultLoggerInstructionNamesThePluginAndKeepsTheThirdPartyLine() {
    String instruction = row("default-logger").module().addInstruction("0.2.2");
    assertThat(instruction)
        .startsWith("with the ai.narrativetrace Gradle plugin, ")
        .contains(
            "narrativeTrace { modules { slf4j.set(true) } } plus"
                + " runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")")
        .contains(
            "without it, runtimeOnly(\"ai.narrativetrace:narrativetrace-slf4j:0.2.2\") and"
                + " runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")")
        .doesNotContain("plus runtimeOnly(\"ai.narrativetrace");
  }

  @Test
  void aModuleWithoutAPluginOrAConfigurationRendersTheBareCoordinate() {
    IntegrationModule agentLike =
        new IntegrationModule(
            List.of("ai.narrativetrace:narrativetrace-agent"),
            null,
            List.of("ai.narrativetrace:narrativetrace-agent"),
            null);
    assertThat(agentLike.addInstruction("0.2.2"))
        .isEqualTo("ai.narrativetrace:narrativetrace-agent:0.2.2");
  }

  // ---- Declared NarrativeTrace version ---------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plugins { id(\"ai.narrativetrace\") version \"0.2.2\" }",
        "plugins { id 'ai.narrativetrace' version '0.2.2' }",
        "implementation 'ai.narrativetrace:narrativetrace-core:0.2.2'"
      })
  void aDeclaredNarrativeTraceVersionIsReadInEitherDsl(String build) {
    DoctorSnapshot s = DoctorSnapshot.builder().buildFileContent(build).build();
    assertThat(s.narrativeTraceVersionOrPlaceholder()).isEqualTo("0.2.2");
  }

  // ---- SnapshotBuilder path classification ----------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {"src2/main/resources/application.yml", "mysrc/main/resources/application.yml"})
  void aDirectoryThatMerelyLooksLikeSrcHoldsNoResourceFile(String rel, @TempDir Path project)
      throws IOException {
    write(project, rel, "narrativetrace:\n  base-packages: com.example\n");
    assertThat(SnapshotBuilder.build(project).resourceFiles()).isEmpty();
  }

  @Test
  void aBuildLogicDirectoryHoldsManifestsNotBuildOutput(@TempDir Path project) throws IOException {
    write(project, "build-logic/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }");
    DoctorSnapshot s = SnapshotBuilder.build(project);
    assertThat(s.manifestFiles()).containsOnlyKeys("build-logic/build.gradle.kts");
  }
}
