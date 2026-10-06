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
package ai.narrativetrace.tooling.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads a real directory once, read-only: what the planner later decides from is decided here. */
class ProjectStateReaderTest {

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static String installedPage(String coordinate) {
    return "---\nname: narrativetrace-doctor\n---\n" + Provenance.line(coordinate) + "\n\nbody\n";
  }

  @Test
  void readsAnEmptyDirectoryAsAnUntouchedProject(@TempDir Path dir) {
    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.agentsMd()).isEmpty();
    assertThat(state.claudeMd()).isEmpty();
    assertThat(state.hasClaudeDirectory()).isFalse();
    assertThat(state.installedSkills()).isEmpty();
    assertThat(state.markedRuleFiles()).isEmpty();
    assertThat(state.outputDirectory()).isEqualTo("build/narrativetrace");
    assertThat(state.gradleProject()).isFalse();
  }

  @Test
  void readsBothContextFilesByteForByte(@TempDir Path dir) throws IOException {
    write(dir.resolve("AGENTS.md"), "# Agents\r\n\r\ntext");
    write(dir.resolve("CLAUDE.md"), "﻿# Claude\n");

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.agentsMd()).hasValue("# Agents\r\n\r\ntext");
    assertThat(state.claudeMd()).hasValue("﻿# Claude\n");
  }

  @Test
  void seesTheVendorDirectoryEvenWhenItHoldsNoSkills(@TempDir Path dir) throws IOException {
    Files.createDirectories(dir.resolve(".claude"));

    assertThat(ProjectStateReader.read(dir).hasClaudeDirectory()).isTrue();
  }

  @Test
  void readsAnInstalledSkillWithItsProvenanceCoordinate(@TempDir Path dir) throws IOException {
    String page = installedPage("ai.narrativetrace:narrativetrace-skills:0.1.0");
    write(dir.resolve(".agents/skills/narrativetrace-doctor/SKILL.md"), page);

    InstalledSkill skill =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(skill.presence()).isEqualTo(Presence.OURS);
    assertThat(skill.coordinate()).isEqualTo("ai.narrativetrace:narrativetrace-skills:0.1.0");
    assertThat(skill.body()).isEqualTo(page);
    assertThat(skill.page().toString()).isEqualTo(".agents/skills/narrativetrace-doctor/SKILL.md");
    assertThat(skill.directory().toString()).isEqualTo(".agents/skills/narrativetrace-doctor");
  }

  @Test
  void readsASkillDirectoryWithoutOurProvenanceAsForeign(@TempDir Path dir) throws IOException {
    write(dir.resolve(".agents/skills/somebody-elses/SKILL.md"), "---\nname: x\n---\nbody\n");
    Files.createDirectories(dir.resolve(".claude/skills/no-page-at-all"));

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.installedSkill(SkillFlavour.AGENTS, "somebody-elses").orElseThrow().presence())
        .isEqualTo(Presence.FOREIGN);
    assertThat(state.installedSkill(SkillFlavour.CLAUDE, "no-page-at-all").orElseThrow().presence())
        .isEqualTo(Presence.FOREIGN);
  }

  @Test
  void readsAFileSittingWhereASkillDirectoryBelongsAsNotADirectory(@TempDir Path dir)
      throws IOException {
    write(dir.resolve(".agents/skills/narrativetrace-doctor"), "not a directory\n");

    InstalledSkill skill =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(skill.presence()).isEqualTo(Presence.NOT_A_DIRECTORY);
    assertThat(skill.body()).isEmpty();
  }

  @Test
  void readsBothFlavoursIndependently(@TempDir Path dir) throws IOException {
    write(
        dir.resolve(".agents/skills/narrativetrace-doctor/SKILL.md"),
        installedPage("ai.narrativetrace:narrativetrace-skills:0.1.0"));
    write(dir.resolve(".claude/skills/narrativetrace-doctor/SKILL.md"), "---\nx\n---\nother\n");

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.installedSkills()).hasSize(2);
    assertThat(
            state
                .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
                .orElseThrow()
                .presence())
        .isEqualTo(Presence.OURS);
    assertThat(
            state
                .installedSkill(SkillFlavour.CLAUDE, "narrativetrace-doctor")
                .orElseThrow()
                .presence())
        .isEqualTo(Presence.FOREIGN);
    assertThat(state.installedSkill(SkillFlavour.CLAUDE, "absent")).isEmpty();
  }

  // --- links ----------------------------------------------------------------------------------

  /**
   * The verified hazard `npx skills add` leaves behind: the agents page is real, and the vendor
   * path is a LINK to it. Following that link would make the installer write the vendor flavour
   * over the agents page, so the reader reports the link instead of resolving it.
   */
  @Test
  void readsASymlinkedSkillDirectoryAsALinkAndNotAsADirectoryOfOurs(@TempDir Path dir)
      throws IOException {
    String page = installedPage("ai.narrativetrace:narrativetrace-skills:0.1.0");
    write(dir.resolve(".agents/skills/narrativetrace-doctor/SKILL.md"), page);
    Files.createDirectories(dir.resolve(".claude/skills"));
    Files.createSymbolicLink(
        dir.resolve(".claude/skills/narrativetrace-doctor"),
        Path.of("../../.agents/skills/narrativetrace-doctor"));

    InstalledSkill linked =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.CLAUDE, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(linked.presence()).isEqualTo(Presence.LINKED_DIRECTORY);
    assertThat(linked.link()).isEqualTo("../../.agents/skills/narrativetrace-doctor");
    assertThat(linked.body()).isEqualTo(page);
    assertThat(linked.linkedAt().toString()).isEqualTo(".claude/skills/narrativetrace-doctor");
  }

  @Test
  void readsASymlinkedPageInsideARealDirectoryAsALinkToo(@TempDir Path dir) throws IOException {
    write(dir.resolve(".agents/skills/somewhere/SKILL.md"), "---\nname: x\n---\nbody\n");
    Files.createDirectories(dir.resolve(".agents/skills/narrativetrace-doctor"));
    Files.createSymbolicLink(
        dir.resolve(".agents/skills/narrativetrace-doctor/SKILL.md"),
        Path.of("../somewhere/SKILL.md"));

    InstalledSkill linked =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(linked.presence()).isEqualTo(Presence.LINKED_PAGE);
    assertThat(linked.body()).isEqualTo("---\nname: x\n---\nbody\n");
    assertThat(linked.linkedAt().toString())
        .isEqualTo(".agents/skills/narrativetrace-doctor/SKILL.md");
  }

  /** A link that resolves to nothing reaches no page: there is nothing to adopt or to remove. */
  @Test
  void readsADanglingLinkAsALinkThatReachesNoPage(@TempDir Path dir) throws IOException {
    Files.createDirectories(dir.resolve(".agents/skills"));
    Files.createSymbolicLink(
        dir.resolve(".agents/skills/narrativetrace-doctor"), Path.of("../../gone"));

    InstalledSkill linked =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(linked.presence()).isEqualTo(Presence.LINKED_DIRECTORY);
    assertThat(linked.body()).isEmpty();
  }

  /**
   * A CHAIN of links the filesystem will happily follow, ending at something that is not a skill
   * directory. The reader follows nothing itself: it reports a link, and the page it looks for
   * behind that link — {@code <name>/SKILL.md} — does not exist, so the body is empty and the
   * planner refuses with "no page at the other end".
   *
   * <p>Worth pinning because the chain resolves: a reader that trusted {@code toRealPath} and read
   * whatever it landed on would hand the planner the target file's bytes under a skill's name, and
   * an install would then stamp a file nobody meant to be a page.
   */
  @Test
  void readsALinkChainEndingAtSomethingThatIsNotASkillDirectoryAsReachingNoPage(@TempDir Path dir)
      throws IOException {
    write(dir.resolve(".agents/skills/target.md"), "not a skill directory\n");
    Files.createSymbolicLink(dir.resolve(".agents/skills/midway"), Path.of("target.md"));
    Files.createSymbolicLink(
        dir.resolve(".agents/skills/narrativetrace-doctor"), Path.of("midway"));

    InstalledSkill linked =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(linked.presence()).isEqualTo(Presence.LINKED_DIRECTORY);
    assertThat(linked.link()).isEqualTo("midway");
    assertThat(linked.body()).as("a chain reaching a plain file reaches no page").isEmpty();
  }

  /**
   * A link OUT of the project reaches no page either, whatever is at the other end. Nothing outside
   * the project is ours to stamp or to delete, and a shared skills directory is exactly the kind of
   * thing a person links to on purpose.
   */
  @Test
  void readsALinkOutOfTheProjectAsReachingNoPage(@TempDir Path dir, @TempDir Path elsewhere)
      throws IOException {
    write(elsewhere.resolve("shared/SKILL.md"), installedPage("ai.narrativetrace:x:1"));
    Files.createDirectories(dir.resolve(".agents/skills"));
    Files.createSymbolicLink(
        dir.resolve(".agents/skills/narrativetrace-doctor"), elsewhere.resolve("shared"));

    InstalledSkill linked =
        ProjectStateReader.read(dir)
            .installedSkill(SkillFlavour.AGENTS, "narrativetrace-doctor")
            .orElseThrow();

    assertThat(linked.presence()).isEqualTo(Presence.LINKED_DIRECTORY);
    assertThat(linked.body()).isEmpty();
    assertThat(linked.link()).isEqualTo(elsewhere.resolve("shared").toString());
  }

  /**
   * A whole install root behind a link is the same hazard one level up, and worse: every skill of
   * that flavour would be written into somebody else's tree, including the ones not there yet. The
   * root is reported as linked and nothing under it is listed, so the planner refuses the flavour
   * once instead of planning writes it cannot make.
   */
  @Test
  void readsASymlinkedInstallRootAsLinkedAndListsNothingUnderIt(
      @TempDir Path dir, @TempDir Path shared) throws IOException {
    write(shared.resolve("narrativetrace-doctor/SKILL.md"), "---\nname: x\n---\nbody\n");
    Files.createDirectories(dir.resolve(".claude"));
    Files.createSymbolicLink(dir.resolve(".claude/skills"), shared);

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.linkedInstallRoot(SkillFlavour.CLAUDE)).hasValue(shared.toString());
    assertThat(state.installedSkills()).isEmpty();
    assertThat(state.linkedInstallRoot(SkillFlavour.AGENTS)).isEmpty();
  }

  @Test
  void readsALegacyRuleFileOnlyWhenItCarriesOurMarkers(@TempDir Path dir) throws IOException {
    String marked = "# rules\n<!-- narrativetrace:start -->\nx\n<!-- narrativetrace:end -->\n";
    write(dir.resolve(".cursorrules"), marked);
    write(dir.resolve(".github/copilot-instructions.md"), "# no markers here\n");

    assertThat(ProjectStateReader.read(dir).markedRuleFiles())
        .containsExactly(java.util.Map.entry(".cursorrules", marked));
  }

  @Test
  void readsTheOutputDirectoryFromGradleProperties(@TempDir Path dir) throws IOException {
    write(dir.resolve("gradle.properties"), "narrativetrace.outputDir=out/narrative\n");

    assertThat(ProjectStateReader.read(dir).outputDirectory()).isEqualTo("out/narrative");
  }

  @Test
  void ignoresAnotherPropertyAndAnEmptyValue(@TempDir Path dir) throws IOException {
    write(dir.resolve("gradle.properties"), "narrativetrace.output=true\nother.outputDir=x\n");

    assertThat(ProjectStateReader.read(dir).outputDirectory()).isEqualTo("build/narrativetrace");

    write(dir.resolve("gradle.properties"), "narrativetrace.outputDir=\n");

    assertThat(ProjectStateReader.read(dir).outputDirectory()).isEqualTo("build/narrativetrace");
  }

  @Test
  void readsARuleFileWhoseMarkersDoNotPairUpSoThePlannerCanRefuseIt(@TempDir Path dir)
      throws IOException {
    String broken = "# rules\n<!-- narrativetrace:start -->\nno end below it\n";
    write(dir.resolve(".cursorrules"), broken);

    assertThat(ProjectStateReader.read(dir).markedRuleFiles())
        .containsExactly(java.util.Map.entry(".cursorrules", broken));
  }

  @Test
  void readsTheOutputDirectoryFromTheBuildFileWhenThePropertiesDoNotSetIt(@TempDir Path dir)
      throws IOException {
    write(
        dir.resolve("build.gradle.kts"),
        "tasks.test {\n  systemProperty(\"narrativetrace.outputDir\", \"build/traces\")\n}\n");

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.outputDirectory()).isEqualTo("build/traces");
    assertThat(state.gradleProject()).isTrue();
  }

  @Test
  void readsTheGroovyBuildFileToo(@TempDir Path dir) throws IOException {
    write(dir.resolve("build.gradle"), "test { systemProperty 'narrativetrace.outputDir', 'out' }");

    ProjectState state = ProjectStateReader.read(dir);

    assertThat(state.outputDirectory()).isEqualTo("out");
    assertThat(state.gradleProject()).isTrue();
  }

  @Test
  void recognisesAGradleProjectBySettingsAlone(@TempDir Path dir) throws IOException {
    write(dir.resolve("settings.gradle.kts"), "rootProject.name = \"x\"\n");

    assertThat(ProjectStateReader.read(dir).gradleProject()).isTrue();
  }

  @Test
  void boundsHowManySkillDirectoriesItReads(@TempDir Path dir) throws IOException {
    for (int i = 0; i < 5; i++) {
      Files.createDirectories(dir.resolve(".agents/skills/skill-" + i));
    }

    ProjectState state = ProjectStateReader.read(dir, 3);

    assertThat(state.installedSkills()).hasSize(3);
    assertThat(state.installedSkills())
        .extracting(InstalledSkill::name)
        .containsExactly("skill-0", "skill-1", "skill-2");
  }

  @Test
  void refusesToReadAProjectThatIsNotADirectory(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("a-file");
    Files.writeString(file, "x");

    assertThatThrownBy(() -> ProjectStateReader.read(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a directory");
    assertThatThrownBy(() -> ProjectStateReader.read(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("project directory must be given");
  }

  @Test
  void failsLoudlyWhenAFileItMustPlanAgainstCannotBeRead(@TempDir Path dir) throws IOException {
    Files.write(dir.resolve("AGENTS.md"), new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd});

    assertThatThrownBy(() -> ProjectStateReader.read(dir))
        .isInstanceOf(java.io.UncheckedIOException.class)
        .hasMessageContaining("AGENTS.md");
  }
}
