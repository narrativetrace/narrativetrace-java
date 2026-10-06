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

import ai.narrativetrace.tooling.doctor.DoctorRender;
import ai.narrativetrace.tooling.doctor.DoctorReport;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import ai.narrativetrace.tooling.doctor.checks.SkillsInstalledCheck;
import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adversarial pass over {@code tooling.doctor} and {@code tooling.init} after milestone 4:
 * behaviour the production code has and no other test exercises.
 *
 * <p>Deliberately in the PARENT package, so only the public surface is reachable — a case that
 * compiles here is a case an entry point could really write.
 *
 * <p>What survived the review: the generated pass proposed twenty-seven cases, of which a third
 * restated assertions the per-class tests already make. Those were dropped. What is left is either
 * a combination no single-feature test reaches (two stale stamps at once, a stale skill beside a
 * missing one), a near miss a boundary rule demands (a skill name that is a prefix of a real one),
 * or an input shape nothing else feeds in (every control character, a single-quoted coordinate, an
 * uppercased property key, a properties line with an empty key).
 */
class AdversarialM4Test {

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:1.0.0";

  private final SkillsInstalledCheck check = new SkillsInstalledCheck();

  private static InstalledSkill ours(String name, String coordinate) {
    return new InstalledSkill(
        SkillFlavour.AGENTS, name, InstalledSkill.Presence.OURS, coordinate, "page");
  }

  private static InstalledSkill theirs(String name) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, InstalledSkill.Presence.FOREIGN, "", "");
  }

  private static DoctorSnapshot.Builder project(String... catalogue) {
    return DoctorSnapshot.builder().carrier(CARRIER, List.of(catalogue));
  }

  // --- the JSON envelope, with the new trailing field -----------------------------------------

  /** A failure means a control character reached the report unescaped: invalid JSON. */
  @Test
  void everyControlCharacterInAMessageLeavesTheJsonWellFormed() {
    for (int code = 0; code <= 0x1F; code++) {
      String message = "text" + (char) code + "end";
      String json =
          DoctorRender.renderJson(
              new DoctorReport(List.of(Finding.pass("id", message, "https://x")), 0));
      assertBalancedJson(json);
      assertThat(json).doesNotContain(message);
    }
  }

  /** A failure means a run of quotes and backslashes is escaped once too few or once too many. */
  @Test
  void consecutiveQuotesAndBackslashesAreEscapedOneForOne() {
    String json =
        DoctorRender.renderJson(
            new DoctorReport(List.of(Finding.pass("id", "a\\\\b\"\"c\\\"d", "https://x")), 0));

    assertThat(json).contains("\\\\\\\\").contains("\\\"\\\"");
  }

  /**
   * A failure means the comma before the new {@code skill} key lands wrong in a multi-finding
   * report — the shape a single-finding test cannot see.
   */
  @Test
  void aReportOfSeveralFindingsStaysWellFormedWithTheSkillKeyLast() {
    String json =
        DoctorRender.renderJson(
            new DoctorReport(
                List.of(
                    Finding.pass("toolchain.jdk-version", "fine", "https://x"),
                    Finding.fail("trap.silent-sink", "no sink", "attach one", "https://x")),
                1));

    assertBalancedJson(json);
    assertThat(json).contains("\"skill\": null").contains("\"skill\": \"narrativetrace-doctor\"");
    assertThat(json.indexOf("\"docUrl\"")).isLessThan(json.indexOf("\"skill\""));
  }

  // --- the skills check, in combinations one feature at a time cannot reach -------------------

  /** A failure means two different stale stamps are reported in arrival order, or one is lost. */
  @Test
  void twoDifferentStaleStampsAreBothNamedInSortedOrder() {
    String older = "ai.narrativetrace:narrativetrace-skills:0.0.1";
    String newer = "ai.narrativetrace:narrativetrace-skills:0.0.2";

    String message =
        check
            .run(
                project("doctor", "clarity")
                    .addInstalledSkill(ours("doctor", newer))
                    .addInstalledSkill(ours("clarity", older))
                    .build())
            .message();

    assertThat(message.indexOf(older)).isPositive().isLessThan(message.indexOf(newer));
  }

  /**
   * A failure means the precedence slipped: a stale stamp and a missing skill together must report
   * the STAMP, because re-running the installer fixes both and the stamp is the root cause.
   */
  @Test
  void aStaleStampBesideAMissingSkillReportsTheStampAndNotTheAbsence() {
    String older = "ai.narrativetrace:narrativetrace-skills:0.0.1";

    Finding finding =
        check.run(project("doctor", "clarity").addInstalledSkill(ours("doctor", older)).build());

    assertThat(finding.message()).contains("installed from " + older).doesNotContain("clarity");
  }

  /**
   * A failure means a project whose every skill path belongs to somebody else is told only that
   * nothing is installed, without being told why.
   */
  @Test
  void whereEverySkillPathIsSomebodyElsesAllOfThemAreNamed() {
    Finding finding =
        check.run(
            project("doctor", "clarity")
                .addInstalledSkill(theirs("doctor"))
                .addInstalledSkill(theirs("clarity"))
                .build());

    assertThat(finding.message()).contains("not ours").contains("doctor").contains("clarity");
  }

  /** A failure means a partial install names one missing skill and silently drops the rest. */
  @Test
  void everyMissingSkillIsNamedNotJustTheFirst() {
    Finding finding =
        check.run(
            project("doctor", "clarity", "custom")
                .addInstalledSkill(ours("doctor", CARRIER))
                .build());

    assertThat(finding.message()).contains("clarity").contains("custom");
  }

  // --- the snapshot's own boundaries ----------------------------------------------------------

  /** A failure means a skill lookup matches a name that merely shares a prefix with a real one. */
  @Test
  void aSkillLookupMatchesTheWholeNameAndNotAPrefixOfIt() {
    DoctorSnapshot snapshot =
        DoctorSnapshot.builder().addInstalledSkill(ours("doctor", CARRIER)).build();

    assertThat(snapshot.installedSkill(SkillFlavour.AGENTS, "doctor")).isPresent();
    assertThat(snapshot.installedSkill(SkillFlavour.AGENTS, "docto")).isEmpty();
    assertThat(snapshot.installedSkill(SkillFlavour.AGENTS, "doctor-pro")).isEmpty();
  }

  /** A failure means clearing a copy's skills reaches back into the snapshot it was copied from. */
  @Test
  void clearingACopysSkillsLeavesTheOriginalWhole() {
    DoctorSnapshot original =
        DoctorSnapshot.builder()
            .addInstalledSkill(ours("doctor", CARRIER))
            .addInstalledSkill(ours("clarity", CARRIER))
            .build();

    DoctorSnapshot copy = original.toBuilder().clearInstalledSkills().build();

    assertThat(copy.installedSkills()).isEmpty();
    assertThat(original.installedSkills()).hasSize(2);
  }

  // --- the project scan's uncovered input shapes ----------------------------------------------

  /** A failure means a carrier nobody could resolve also costs the report its installed skills. */
  @Test
  void withNoCarrierTheInstalledSkillsAreStillRead(@TempDir Path project) throws IOException {
    Path page = project.resolve(".agents/skills/doctor/SKILL.md");
    Files.createDirectories(page.getParent());
    Files.writeString(
        page,
        "---\nname: doctor\n---\n\n<!-- installed by narrativetrace init from "
            + CARRIER
            + " — edit the catalogue, not this file -->\n");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project, null);

    assertThat(snapshot.carrierResolved()).isFalse();
    assertThat(snapshot.installedSkills()).hasSize(1);
  }

  /**
   * A failure means a Groovy build file's single-quoted coordinates are invisible to every
   * dependency-reading check.
   */
  @Test
  void aSingleQuotedCoordinateIsReadLikeADoubleQuotedOne(@TempDir Path project) throws IOException {
    Files.writeString(
        project.resolve("build.gradle"),
        "dependencies {\n"
            + "    api('group.one:artifact-one:1.0')\n"
            + "    implementation(\"group.two:artifact-two:2.0\")\n"
            + "}\n");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project);

    assertThat(snapshot.declaredDependencyCoordinates())
        .contains("group.one:artifact-one:1.0", "group.two:artifact-two:2.0");
  }

  /** A failure means a properties line with an empty key becomes an empty-named property. */
  @Test
  void aPropertiesLineWithNoKeyIsSkippedRatherThanStoredUnderAnEmptyName(@TempDir Path project)
      throws IOException {
    Files.writeString(project.resolve("gradle.properties"), "=orphan\nreal=value\n");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project);

    assertThat(snapshot.gradleProperties()).containsEntry("real", "value").doesNotContainKey("");
  }

  /**
   * A failure means an uppercased autodetection key stops counting as registration, though the
   * pattern claims to ignore case.
   */
  @Test
  void anUppercasedAutodetectionKeyStillCountsAsRegistration(@TempDir Path project)
      throws IOException {
    Path resources = project.resolve("src/test/resources");
    Files.createDirectories(resources);
    Files.writeString(
        resources.resolve("junit-platform.properties"),
        "JUNIT.JUPITER.EXTENSIONS.AUTODETECTION.ENABLED=TRUE\n");

    assertThat(SnapshotBuilder.build(project).extensionRegisteredViaServiceLoader()).isTrue();
  }

  /** A failure means the skill table stops answering outside its own package. */
  @Test
  void aFindingBuiltFromAnotherPackageStillCarriesItsFixingSkill() {
    assertThat(Finding.fail("trap.silent-sink", "no sink", "attach one", "https://x").skill())
        .isEqualTo("narrativetrace-doctor");
  }

  /**
   * Braces, brackets and string terminators balance — the most a zero-dependency module can assert
   * about its own hand-written JSON without taking a parser as a test dependency. The installer's
   * own {@code JsonReader} cannot stand in: it reads the carrier catalogue's subset, which has no
   * numbers and no {@code null}, and the doctor's envelope has both.
   */
  private static void assertBalancedJson(String json) {
    assertThat(json).startsWith("{\n  \"findings\": [").endsWith("}\n");
    Nesting nesting = nestingOf(json);
    assertThat(nesting.inString()).as("unterminated string in " + json).isFalse();
    assertThat(nesting.braces()).as("unbalanced braces in " + json).isZero();
    assertThat(nesting.brackets()).as("unbalanced brackets in " + json).isZero();
  }

  /** How deep a scan of the whole document ended — zero on both counts, outside every string. */
  private record Nesting(int braces, int brackets, boolean inString) {}

  private static Nesting nestingOf(String json) {
    int braces = 0;
    int brackets = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < json.length(); i++) {
      char c = json.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (inString && c == '\\') {
        escaped = true;
      } else if (c == '"') {
        inString = !inString;
      } else if (!inString) {
        braces += c == '{' ? 1 : c == '}' ? -1 : 0;
        brackets += c == '[' ? 1 : c == ']' ? -1 : 0;
      }
    }
    return new Nesting(braces, brackets, inString);
  }
}
