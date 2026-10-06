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

import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Feature-combination coverage for {@link RefreshPlanner} beyond {@code RefreshPlannerTest}: vendor
 * rule files, both skill flavours going stale together, and a foreign directory under one flavour
 * sitting beside a stale directory of ours under another.
 */
class RefreshPlannerAdversarialM3Test {

  private static final String OLD = "ai.narrativetrace:narrativetrace-skills:0.0.1";

  private Carrier carrier;

  @BeforeEach
  void openACarrier(@TempDir Path dir) {
    carrier = Carriers.fake(dir, "doctor");
  }

  private static ProjectState.Builder project() {
    return ProjectState.builder();
  }

  /** A vendor rule file is never created by a build — only kept current when it is already ours. */
  @Test
  void rewritesAStaleMarkedBlockInAVendorRuleFileWithNoAgentsMdOrClaudeMdPresent() {
    Path rulePath = Path.of(".cursor/rules/narrativetrace.mdc");
    String stale =
        "# Cursor rules\n\n<!-- narrativetrace:start "
            + OLD
            + " -->\nstale\n<!-- narrativetrace:end -->\n";
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", OLD))
            .markedRuleFile(rulePath.toString(), stale)
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions()).extracting(Action::path).contains(rulePath);
    assertThat(after(plan, rulePath)).contains(carrier.coordinate());
  }

  /**
   * Auto vendor detection turns BOTH flavours on when a {@code .claude} directory is present; a
   * refresh must rewrite whichever of the two is stale, not just the open-standard one every other
   * test in this suite happens to use.
   */
  @Test
  void rewritesBothFlavoursWhenTheClaudeDirectoryTurnsVendorDetectionOn() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(ours("doctor", OLD))
            .addInstalledSkill(oursClaude("doctor", OLD))
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions())
        .extracting(Action::path)
        .containsExactlyInAnyOrder(
            Path.of(".agents/skills/doctor/SKILL.md"), Path.of(".claude/skills/doctor/SKILL.md"));
  }

  /**
   * The vendor copy of the SAME skill name can be foreign while the open-standard copy is ours and
   * stale. The refresh must rewrite the one that is ours and silently skip the one that is not,
   * carrying no refusal for it either — a build never asks for {@code --force}.
   */
  @Test
  void neverTouchesAForeignSkillUnderTheVendorFlavourWhileRewritingTheStaleOpenStandardOne() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(ours("doctor", OLD))
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.CLAUDE, "doctor", Presence.FOREIGN, "", "# theirs\n"))
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions())
        .extracting(Action::path)
        .containsExactly(Path.of(".agents/skills/doctor/SKILL.md"));
    assertThat(plan.hasRefusals()).isFalse();
  }

  /**
   * A vendor rule file carrying two NarrativeTrace sections is a refusal at install time; a refresh
   * must never surface that refusal — it drops everything but a {@code ReplaceBlock}, so the broken
   * rule file is simply left alone rather than reported.
   */
  @Test
  void neverCarriesARefusalFromAVendorRuleFileWithTwoMarkedSections() {
    String twoRegions =
        "<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
            + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n";
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", OLD))
            .markedRuleFile("RULES.md", twoRegions)
            .build();

    InitPlan plan = RefreshPlanner.plan(state, carrier);

    assertThat(plan.actions()).noneMatch(action -> action.path().equals(Path.of("RULES.md")));
    assertThat(plan.hasRefusals()).isFalse();
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

  private InstalledSkill oursClaude(String name, String coordinate) {
    return new InstalledSkill(
        SkillFlavour.CLAUDE,
        name,
        Presence.OURS,
        coordinate,
        Provenance.stamp(Carriers.body(name, SkillFlavour.CLAUDE), coordinate));
  }
}
