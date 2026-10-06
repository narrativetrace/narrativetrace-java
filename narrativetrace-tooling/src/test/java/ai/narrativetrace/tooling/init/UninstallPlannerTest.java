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

import ai.narrativetrace.tooling.init.Action.DeleteDirectory;
import ai.narrativetrace.tooling.init.Action.DeleteFile;
import ai.narrativetrace.tooling.init.Action.FileEdit;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Uninstall removes EXACTLY what the installer wrote and nothing beside it. Every case here is a
 * "leave it alone" as much as it is a "remove it": a foreign directory, a line that is not the line
 * we added, a file a person has since written in.
 */
class UninstallPlannerTest {

  private static final Path AGENTS_MD = Path.of("AGENTS.md");
  private static final Path CLAUDE_MD = Path.of("CLAUDE.md");
  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static final String BLOCK =
      "<!-- narrativetrace:start "
          + COORDINATE
          + " -->\n## NarrativeTrace\n"
          + "<!-- narrativetrace:end -->\n";

  private static InitPlan plan(ProjectState state) {
    return UninstallPlanner.plan(state, InitOptions.defaults());
  }

  private static Optional<Action> actionOn(InitPlan plan, Path path) {
    return plan.actions().stream().filter(action -> action.path().equals(path)).findFirst();
  }

  private static String after(InitPlan plan, Path path) {
    return ((FileEdit) actionOn(plan, path).orElseThrow()).after();
  }

  private static InstalledSkill ours(String coordinate) {
    return new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.OURS, coordinate, "page\n");
  }

  // --- skills ---------------------------------------------------------------------------------

  @Test
  void removesASkillPageAndThenItsDirectory() {
    InitPlan plan = plan(ProjectState.builder().addInstalledSkill(ours(COORDINATE)).build());

    assertThat(actionOn(plan, Path.of(".agents/skills/doctor/SKILL.md")))
        .get()
        .isInstanceOf(DeleteFile.class);
    assertThat(actionOn(plan, Path.of(".agents/skills/doctor")))
        .get()
        .isInstanceOf(DeleteDirectory.class);
    assertThat(
            plan.actions().indexOf(actionOn(plan, Path.of(".agents/skills/doctor")).orElseThrow()))
        .as("the directory goes after the page it held")
        .isEqualTo(1);
  }

  @Test
  void removesASkillInstalledFromAnyCarrierVersion() {
    InitPlan plan =
        plan(
            ProjectState.builder()
                .addInstalledSkill(ours("ai.narrativetrace:narrativetrace-skills:0.0.1"))
                .build());

    assertThat(plan.actions()).hasSize(2);
  }

  @Test
  void leavesASkillDirectorySomebodyElseOwnsCompletelyAlone() {
    ProjectState state =
        ProjectState.builder()
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.AGENTS, "theirs", Presence.FOREIGN, "", "page\n"))
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.CLAUDE, "a-file", Presence.NOT_A_DIRECTORY, "", ""))
            .build();

    assertThat(plan(state).actions()).isEmpty();
    assertThat(plan(state).carrier()).isEqualTo("ai.narrativetrace:narrativetrace-skills:unknown");
  }

  /**
   * A link is never something the installer created, so an uninstall leaves it — link and target
   * both. Following one would delete a page at the other end that may not even be in this project,
   * and a page reached through a link reads as OURS whenever it carries the provenance line.
   */
  @Test
  void leavesASymbolicLinkAndWhateverItPointsAtAlone() {
    ProjectState state =
        ProjectState.builder()
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.CLAUDE,
                    "doctor",
                    Presence.LINKED_DIRECTORY,
                    "",
                    "page\n" + Provenance.line(COORDINATE) + "\n",
                    "../../.agents/skills/doctor"))
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.AGENTS,
                    "doctor",
                    Presence.LINKED_PAGE,
                    "",
                    "page\n",
                    "../elsewhere/SKILL.md"))
            .build();

    assertThat(plan(state).actions()).isEmpty();
  }

  // --- the managed section --------------------------------------------------------------------

  @Test
  void removesTheSectionAndLeavesTheRestByteForByte() {
    String text = "# Agents\n\n" + BLOCK + "\ntail\n";
    ProjectState state = ProjectState.builder().agentsMd(text).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo("# Agents\n\ntail\n");
  }

  @Test
  void deletesAFileTheInstallerCreatedOnceNothingOfItIsLeft() {
    String created = "<!-- narrativetrace:created -->\n" + BLOCK;

    InitPlan plan = plan(ProjectState.builder().agentsMd(created).build());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(DeleteFile.class);
    assertThat(after(plan, AGENTS_MD)).isEmpty();
  }

  /**
   * The blank line before "my own notes" is the SEPARATOR THAT PERSON TYPED, not ours: our own
   * separator, when there is one, sits before our section and goes with it. Removing exactly what
   * the installer wrote means leaving that line where it is.
   */
  @Test
  void keepsAFileTheInstallerCreatedOnceSomebodyElseHasWrittenInIt() {
    String created = "<!-- narrativetrace:created -->\n" + BLOCK + "\nmy own notes\n";

    InitPlan plan = plan(ProjectState.builder().agentsMd(created).build());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo("\nmy own notes\n");
  }

  @Test
  void neverDeletesAFileTheInstallerOnlyAppendedTo() {
    InitPlan plan = plan(ProjectState.builder().agentsMd(BLOCK).build());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEmpty();
  }

  @Test
  void leavesAFileWithNoSectionOfOursAlone() {
    assertThat(plan(ProjectState.builder().agentsMd("# Agents\n").build()).actions()).isEmpty();
  }

  @Test
  void refusesAFileWithTwoSections() {
    InitPlan plan = plan(ProjectState.builder().agentsMd(BLOCK + "\n" + BLOCK).build());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(Refuse.class);
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  @Test
  void refusesAFileWhoseMarkersDoNotPairUp() {
    InitPlan plan =
        plan(ProjectState.builder().agentsMd("<!-- narrativetrace:start -->\nx\n").build());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(Refuse.class);
  }

  // --- the import line ------------------------------------------------------------------------

  @Test
  void removesTheExactImportLineItAdded() {
    ProjectState state = ProjectState.builder().claudeMd("# Project\n\n@AGENTS.md\n").build();

    InitPlan plan = plan(state);

    assertThat(after(plan, CLAUDE_MD)).isEqualTo("# Project\n");
  }

  @Test
  void leavesALineThatIsNotTheExactLineItAdded() {
    ProjectState spaces = ProjectState.builder().claudeMd("# P\n\n@AGENTS.md  \n").build();
    ProjectState fenced = ProjectState.builder().claudeMd("```\n@AGENTS.md\n```\n").build();
    ProjectState quoted = ProjectState.builder().claudeMd("see `@AGENTS.md`\n").build();

    assertThat(plan(spaces).actions()).isEmpty();
    assertThat(plan(fenced).actions()).isEmpty();
    assertThat(plan(quoted).actions()).isEmpty();
  }

  @Test
  void doesNothingWhenThereIsNoVendorContextFile() {
    assertThat(plan(ProjectState.builder().build()).actions()).isEmpty();
  }

  // --- rule files, scope and the plan itself ---------------------------------------------------

  @Test
  void removesOurSectionFromAVendorRuleFileButNeverTheFile() {
    String rules = "# rules\n\n" + BLOCK;
    ProjectState state = ProjectState.builder().markedRuleFile(".cursorrules", rules).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, Path.of(".cursorrules"))).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, Path.of(".cursorrules"))).isEqualTo("# rules\n");
  }

  @Test
  void removesOnlyTheHalfItWasAskedFor() {
    ProjectState state =
        ProjectState.builder().addInstalledSkill(ours(COORDINATE)).agentsMd(BLOCK).build();

    InitPlan skillsOnly =
        UninstallPlanner.plan(state, InitOptions.defaults().withScope(Scope.SKILLS));
    InitPlan sectionOnly =
        UninstallPlanner.plan(state, InitOptions.defaults().withScope(Scope.AGENTS_MD));

    assertThat(actionOn(skillsOnly, AGENTS_MD)).isEmpty();
    assertThat(skillsOnly.actions()).hasSize(2);
    assertThat(actionOn(sectionOnly, AGENTS_MD)).isPresent();
    assertThat(sectionOnly.actions()).hasSize(1);
  }

  @Test
  void namesTheCarrierTheProjectWasInstalledFrom() {
    ProjectState fromSection = ProjectState.builder().agentsMd(BLOCK).build();
    ProjectState fromSkill = ProjectState.builder().addInstalledSkill(ours(COORDINATE)).build();
    ProjectState fromNothing = ProjectState.builder().build();

    assertThat(plan(fromSection).carrier()).isEqualTo(COORDINATE);
    assertThat(plan(fromSkill).carrier()).isEqualTo(COORDINATE);
    assertThat(plan(fromNothing).carrier()).endsWith(":unknown");
  }

  @Test
  void reportsTheUnknownCarrierWhenTheSectionCarriesNoStamp() {
    String unstamped = "<!-- narrativetrace:start -->\nhand written\n<!-- narrativetrace:end -->\n";

    InitPlan plan = plan(ProjectState.builder().agentsMd(unstamped).build());

    assertThat(plan.carrier()).isEqualTo("ai.narrativetrace:narrativetrace-skills:unknown");
    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
  }

  @Test
  void anUninstallOfNothingIsAnEmptyPlanThatExitsZero() {
    InitPlan plan = plan(ProjectState.builder().build());

    assertThat(plan.isEmpty()).isTrue();
    assertThat(plan.exitCode()).isZero();
  }

  @Test
  void carriesTheDryRunFlag() {
    InitPlan plan =
        UninstallPlanner.plan(
            ProjectState.builder().agentsMd(BLOCK + "\n" + BLOCK).build(),
            InitOptions.defaults().withDryRun(true));

    assertThat(plan.dryRun()).isTrue();
    assertThat(plan.exitCode()).isZero();
  }

  @Test
  void refusesToPlanWithoutASnapshotOrOptions() {
    assertThatThrownBy(() -> UninstallPlanner.plan(null, InitOptions.defaults()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UninstallPlanner.plan(ProjectState.builder().build(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
