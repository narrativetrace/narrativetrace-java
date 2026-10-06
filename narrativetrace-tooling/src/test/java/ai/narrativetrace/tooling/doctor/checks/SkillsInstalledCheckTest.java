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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillsInstalledCheckTest {

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:1.2.3";
  private static final String OLDER = "ai.narrativetrace:narrativetrace-skills:0.0.1";
  private static final List<String> CATALOGUE =
      List.of("narrativetrace-doctor", "add-narrative-tracing");

  private final SkillsInstalledCheck check = new SkillsInstalledCheck();

  private static DoctorSnapshot.Builder project() {
    return DoctorSnapshot.builder().carrier(CARRIER, CATALOGUE);
  }

  private static InstalledSkill installed(String name, String coordinate) {
    return new InstalledSkill(
        SkillFlavour.AGENTS, name, InstalledSkill.Presence.OURS, coordinate, "page");
  }

  private static InstalledSkill theirs(String name) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, InstalledSkill.Presence.FOREIGN, "", "");
  }

  @Test
  void passesWhenEveryCatalogueSkillIsInstalledFromTheResolvedCarrier() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", CARRIER))
                .addInstalledSkill(installed("add-narrative-tracing", CARRIER))
                .build());

    assertThat(f.id()).isEqualTo(SkillsInstalledCheck.ID);
    assertThat(f.isFailing()).isFalse();
    assertThat(f.message()).contains("2").contains(CARRIER);
  }

  /**
   * Offline is not a defect. The doctor cannot look up what the project resolves, so it says so and
   * passes — the same two-state shape the JUnit range check uses for "no JUnit in use".
   */
  @Test
  void passesAndSaysSoWhenTheCarrierCouldNotBeResolved() {
    Finding f =
        check.run(
            DoctorSnapshot.builder()
                .addInstalledSkill(installed("narrativetrace-doctor", OLDER))
                .build());

    assertThat(f.isFailing()).isFalse();
    assertThat(f.message()).contains("could not be resolved").contains("cannot tell");
  }

  @Test
  void failsWhenNothingIsInstalledAndNamesBothWaysToInstall() {
    Finding f = check.run(project().build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("not installed");
    assertThat(f.fix())
        .contains("./gradlew narrativetraceInit --diff")
        .contains("narrativetrace init --dry-run");
  }

  /**
   * A project that never ran {@code init} fails rather than being excused: the doctor's audience is
   * a project that already has the library, and "nobody installed the skills" is precisely the
   * thing this check exists to say out loud.
   */
  @Test
  void aProjectWithNoSkillDirectoriesAtAllFails() {
    Finding f = check.run(project().build());
    assertThat(f.isFailing()).isTrue();
  }

  @Test
  void failsNamingBothCoordinatesWhenTheInstalledPagesAreStale() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", OLDER))
                .addInstalledSkill(installed("add-narrative-tracing", OLDER))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("installed from " + OLDER).contains("resolves " + CARRIER);
    assertThat(f.fix()).contains("narrativetraceInit");
  }

  @Test
  void failsNamingTheMissingSkillsWhenOnlySomeAreInstalled() {
    Finding f =
        check.run(project().addInstalledSkill(installed("narrativetrace-doctor", CARRIER)).build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message())
        .contains("add-narrative-tracing")
        .doesNotContain("narrativetrace-doctor");
  }

  /** A directory of somebody else's is reported, and never counted as one of ours. */
  @Test
  void aForeignDirectoryAtASkillsPathIsReportedAndNotCounted() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", CARRIER))
                .addInstalledSkill(theirs("add-narrative-tracing"))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message())
        .contains("add-narrative-tracing")
        .contains("not ours")
        .doesNotContain("narrativetrace-doctor");
  }

  /**
   * "There, but not ours" is most often a REGISTRY install (`npx skills add`, a plugin, a workspace
   * skills install): the pages are this release's own rendered files with no provenance line. The
   * fix has to say so, and say that `init` adopts an identical page rather than demanding --force —
   * otherwise the only reading left is "overwrite somebody else's work".
   */
  @Test
  void theFixForPagesThatAreThereButNotOursNamesTheRegistryCaseAndAdoption() {
    Finding f = check.run(project().addInstalledSkill(theirs("narrativetrace-doctor")).build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("not ours");
    assertThat(f.fix())
        .contains("registry")
        .contains("./gradlew narrativetraceInit --diff")
        .contains("read the diff")
        .contains("adopted");
    assertThat(f.skill()).as("no skill can install the skills").isNull();
  }

  /** Nothing there at all is nobody's registry: that fix says what to run and blames no tool. */
  @Test
  void theFixForAnEmptyProjectDoesNotInventARegistry() {
    assertThat(check.run(project().build()).fix()).doesNotContain("registry");
  }

  /** A partial install is the same story per page, so it makes the same promise about adoption. */
  @Test
  void theFixForAPartialInstallSaysAnIdenticalPageIsAdopted() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", CARRIER))
                .addInstalledSkill(theirs("add-narrative-tracing"))
                .build());

    assertThat(f.fix()).contains("adopted").contains("--force");
  }

  /** A project's own skills are its own business — only the catalogue's names are this check's. */
  @Test
  void aSkillDirectoryTheCatalogueNeverNamedIsIgnored() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", CARRIER))
                .addInstalledSkill(installed("add-narrative-tracing", CARRIER))
                .addInstalledSkill(theirs("deploy-to-staging"))
                .build());

    assertThat(f.isFailing()).isFalse();
    assertThat(f.message()).doesNotContain("deploy-to-staging");
  }

  /**
   * {@code .agents/skills/} is the layout every project gets; the vendor copy is written only where
   * a project is detected as that vendor's, so a vendor-only install is an install that did not
   * happen.
   */
  @Test
  void theVendorCopyAloneIsNotAnInstall() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(
                    new InstalledSkill(
                        SkillFlavour.CLAUDE,
                        "narrativetrace-doctor",
                        InstalledSkill.Presence.OURS,
                        CARRIER,
                        "page"))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("not installed");
  }

  /** Something that is not a directory at all sits where a skill belongs: missing, not ours. */
  @Test
  void aFileWhereASkillDirectoryBelongsCountsAsMissing() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", CARRIER))
                .addInstalledSkill(
                    new InstalledSkill(
                        SkillFlavour.AGENTS,
                        "add-narrative-tracing",
                        InstalledSkill.Presence.NOT_A_DIRECTORY,
                        "",
                        ""))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("add-narrative-tracing").contains("not ours");
  }

  /** Stale beats missing: a person who re-runs init fixes both, and the stamp is the root cause. */
  @Test
  void aStaleAndAMissingSkillTogetherReportTheStamp() {
    Finding f =
        check.run(project().addInstalledSkill(installed("narrativetrace-doctor", OLDER)).build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("installed from " + OLDER);
  }

  @Test
  void everyOutcomeCarriesTheSameStableIdAndAPublishedDocUrl() {
    for (DoctorSnapshot snapshot :
        List.of(
            DoctorSnapshot.builder().build(),
            project().build(),
            project().addInstalledSkill(installed("narrativetrace-doctor", OLDER)).build())) {
      Finding f = check.run(snapshot);
      assertThat(f.id()).isEqualTo("config.skills-installed");
      assertThat(f.docUrl()).startsWith("https://narrativetrace.ai/docs/");
    }
  }

  @Test
  void refusesToRunWithoutASnapshot() {
    assertThatThrownBy(() -> check.run(null)).isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * The two entry points stamp a page with the coordinate of the archive they installed FROM, and
   * those archives differ: the Gradle plugin resolves the carrier jar, the CLI reads the very same
   * resources out of its own jar. Both ship at one family version, and the pages inside them are
   * byte-identical, so the release is what "current" means here. Comparing whole coordinates would
   * report every project installed by one entry point and diagnosed by the other as stale.
   */
  @Test
  void aPageStampedByAnotherArtifactAtTheSameReleaseIsCurrent() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(
                    installed(
                        "narrativetrace-doctor", "ai.narrativetrace:narrativetrace-cli:1.2.3"))
                .addInstalledSkill(
                    installed(
                        "add-narrative-tracing", "ai.narrativetrace:narrativetrace-cli:1.2.3"))
                .build());

    assertThat(f.isFailing()).isFalse();
  }

  @Test
  void aPageStampedAtAnotherReleaseIsStaleWhateverTheArtifactIs() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(
                    installed(
                        "narrativetrace-doctor", "ai.narrativetrace:narrativetrace-cli:0.0.1"))
                .addInstalledSkill(
                    installed(
                        "add-narrative-tracing", "ai.narrativetrace:narrativetrace-cli:0.0.1"))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("ai.narrativetrace:narrativetrace-cli:0.0.1");
  }

  /** A hand-edited stamp that is not a coordinate at all is compared whole, and so is stale. */
  @Test
  void aStampThatNamesNoVersionIsComparedAsItStands() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", "hand-edited"))
                .addInstalledSkill(installed("add-narrative-tracing", "hand-edited"))
                .build());

    assertThat(f.isFailing()).isTrue();
    assertThat(f.message()).contains("hand-edited");
  }

  /** Two different stale stamps are both named, sorted, so a reader sees the whole picture. */
  @Test
  void everyDistinctStaleStampIsNamedOnce() {
    Finding f =
        check.run(
            project()
                .addInstalledSkill(installed("narrativetrace-doctor", OLDER))
                .addInstalledSkill(
                    installed(
                        "add-narrative-tracing", "ai.narrativetrace:narrativetrace-skills:0.0.2"))
                .build());

    assertThat(f.message())
        .contains(OLDER)
        .contains("ai.narrativetrace:narrativetrace-skills:0.0.2");
  }

  /**
   * A stamp is trusted for its version segment only when it IS a three-part coordinate. A stamp
   * with a missing or an extra segment names no release anybody can place, so it is compared whole
   * and reads as stale — the same answer a hand-edited stamp gets.
   */
  @Test
  void aStampWithTheWrongNumberOfSegmentsIsStaleEvenWhenItEndsInTheRightVersion() {
    for (String malformed : List.of(":1.2.3", "narrativetrace-skills:1.2.3", "a:b:c:1.2.3")) {
      Finding f =
          check.run(
              project()
                  .addInstalledSkill(installed("narrativetrace-doctor", malformed))
                  .addInstalledSkill(installed("add-narrative-tracing", malformed))
                  .build());

      assertThat(f.isFailing()).as(malformed).isTrue();
      assertThat(f.message()).contains(malformed);
    }
  }

  /** Nothing installed and nothing foreign either: the message says so and invents no owner. */
  @Test
  void whereNoSkillDirectoryExistsAtAllNobodyElseIsBlamed() {
    Finding f = check.run(project().build());

    assertThat(f.message()).contains("not installed").doesNotContain("not ours");
  }
}
