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
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The refresh-on-build plan: the narrow, never-creating half of an install. Pure, like the other
 * two planners, so every rule here is a unit test with no disk and no build at all.
 */
class RefreshPlannerTest {

  private static final String OLD = "ai.narrativetrace:narrativetrace-skills:0.0.1";

  private Carrier carrier;

  @BeforeEach
  void openACarrier(@TempDir Path dir) {
    carrier = Carriers.fake(dir, "doctor");
  }

  private static ProjectState.Builder project() {
    return ProjectState.builder();
  }

  @Test
  void plansNothingForAProjectThatNeverRanInit() {
    InitPlan plan = RefreshPlanner.plan(project().build(), carrier);

    assertThat(plan.isEmpty()).isTrue();
  }

  @Test
  void rewritesAStaleSkillPageInPlace() {
    ProjectState state = project().addInstalledSkill(ours("doctor", OLD)).build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions())
        .extracting(Action::path)
        .contains(Path.of(".agents/skills/doctor/SKILL.md"));
    assertThat(plan.actions()).allMatch(Action.ReplaceBlock.class::isInstance);
  }

  @Test
  void plansNothingWhenEveryInstalledSkillAlreadyCarriesTheCarriersCoordinate() {
    ProjectState state = project().addInstalledSkill(ours("doctor", carrier.coordinate())).build();

    assertThat(RefreshPlanner.plan(state, carrier).isEmpty()).isTrue();
  }

  /**
   * A stamp that already matches means a build has nothing to say, whatever the page now contains.
   * Somebody editing an installed page has their reasons; a build that silently reverted them would
   * be a build that edits source on its own, which is the whole thing D11 is fenced against. Re-run
   * {@code init} to overwrite it deliberately.
   */
  @Test
  void leavesAPageAloneWhenItsStampMatchesHoweverItHasBeenEdited() {
    InstalledSkill edited =
        new InstalledSkill(
            SkillFlavour.AGENTS,
            "doctor",
            Presence.OURS,
            carrier.coordinate(),
            Provenance.stamp(
                "---\nname: doctor\n---\n\nsomebody's own words\n", carrier.coordinate()));

    InitPlan plan = RefreshPlanner.plan(project().addInstalledSkill(edited).build(), carrier);

    assertThat(plan.isEmpty()).isTrue();
  }

  @Test
  void neverCreatesASkillTheProjectDoesNotHave(@TempDir Path dir) {
    Carrier two = Carriers.fake(dir.resolve("two"), "doctor", "clarity");
    ProjectState state = project().addInstalledSkill(ours("doctor", OLD)).build();

    InitPlan plan = RefreshPlanner.plan(state, two);

    assertThat(plan.actions())
        .extracting(Action::path)
        .containsExactly(Path.of(".agents/skills/doctor/SKILL.md"));
  }

  /**
   * A project carrying a skill this carrier never heard of, beside a stale one it does: the stale
   * page is rewritten and the stranger is left entirely alone. Neither half is new on its own — a
   * refresh that reached for the CATALOGUE instead of the project would only show it here, with
   * both at once (adversarial pass, milestone 5).
   */
  @Test
  void refreshesTheStalePageAndLeavesASkillTheCarrierDoesNotCarry() {
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", OLD))
            .addInstalledSkill(ours("harvester", Carriers.FAKE_COORDINATE))
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions())
        .extracting(Action::path)
        .containsExactly(Path.of(".agents/skills/doctor/SKILL.md"));
  }

  @Test
  void neverAppendsASectionToAnAgentsMdThatCarriesNone() {
    ProjectState state =
        project().addInstalledSkill(ours("doctor", OLD)).agentsMd("# Agents\n").build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions()).noneMatch(action -> action.path().equals(Path.of("AGENTS.md")));
  }

  @Test
  void rewritesOurOwnSectionBesideThePages() {
    String agents =
        "# Agents\n\n<!-- narrativetrace:start "
            + OLD
            + " -->\nstale\n"
            + "<!-- narrativetrace:end -->\n";
    ProjectState state = project().addInstalledSkill(ours("doctor", OLD)).agentsMd(agents).build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions()).extracting(Action::path).contains(Path.of("AGENTS.md"));
    assertThat(after(plan, Path.of("AGENTS.md"))).contains(carrier.coordinate());
  }

  @Test
  void neverAddsTheImportLineToAClaudeMd() {
    ProjectState state =
        project().addInstalledSkill(ours("doctor", OLD)).claudeMd("# Claude\n").build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions()).noneMatch(action -> action.path().equals(Path.of("CLAUDE.md")));
  }

  @Test
  void neverTouchesASkillDirectorySomebodyElseOwns() {
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", OLD))
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.AGENTS, "theirs", Presence.FOREIGN, "", "# theirs\n"))
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions())
        .extracting(Action::path)
        .containsExactly(Path.of(".agents/skills/doctor/SKILL.md"));
  }

  @Test
  void neverCarriesARefusal(@TempDir Path dir) {
    Carrier two = Carriers.fake(dir.resolve("two"), "doctor", "clarity");
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", OLD))
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.AGENTS, "clarity", Presence.NOT_A_DIRECTORY, "", ""))
            .agentsMd(
                "# Agents\n\n<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
                    + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n")
            .build();

    InitPlan plan = RefreshPlanner.plan(state, two);

    assertThat(plan.hasRefusals()).isFalse();
    assertThat(plan.exitCode()).isZero();
  }

  /**
   * The question a build asks BEFORE it reaches for a carrier: a project that never ran init must
   * never resolve one, or every offline build of every project would warn about skills nobody
   * installed.
   */
  @Test
  void knowsWhetherAProjectCarriesAnInstallOfOursAtAll() {
    assertThat(RefreshPlanner.isInstalled(project().build())).isFalse();
    assertThat(RefreshPlanner.isInstalled(project().addInstalledSkill(ours("doctor", OLD)).build()))
        .isTrue();
  }

  @Test
  void aSkillDirectorySomebodyElseOwnsIsNotAnInstallOfOurs() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.AGENTS, "theirs", Presence.FOREIGN, "", "# theirs\n"))
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.CLAUDE, "gone", Presence.NOT_A_DIRECTORY, "", ""))
            .build();

    assertThat(RefreshPlanner.isInstalled(state)).isFalse();
  }

  @Test
  void refusesToAnswerWhetherNothingIsInstalled() {
    assertThatThrownBy(() -> RefreshPlanner.isInstalled(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesToPlanWithoutAStateOrACarrier() {
    assertThatThrownBy(() -> RefreshPlanner.plan(null, carrier))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RefreshPlanner.plan(project().build(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static String after(InitPlan plan, Path path) {
    return plan.actions().stream()
        .filter(action -> action.path().equals(path))
        .map(Action.FileEdit.class::cast)
        .map(Action.FileEdit::after)
        .findFirst()
        .orElseThrow();
  }

  private InstalledSkill ours(String name, String coordinate) {
    return new InstalledSkill(
        SkillFlavour.AGENTS,
        name,
        Presence.OURS,
        coordinate,
        Provenance.stamp(Carriers.body(name, SkillFlavour.AGENTS), coordinate));
  }
}
