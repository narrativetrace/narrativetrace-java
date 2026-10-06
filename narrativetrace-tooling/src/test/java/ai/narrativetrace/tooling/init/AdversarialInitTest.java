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

import ai.narrativetrace.tooling.init.Action.AppendBlock;
import ai.narrativetrace.tooling.init.Action.CreateFile;
import ai.narrativetrace.tooling.init.Action.FileEdit;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Feature COMBINATIONS, from the adversarial pass that closes the milestone: a flag that changes
 * one decision while another decision still refuses, two skills where only one is somebody else's,
 * a scope that silences half the plan while the other half still has an opinion.
 *
 * <p>Each case here is an interaction no single-feature test exercises. Two of them started life
 * asserting the opposite of what the code does; the code was right both times, and the cases now
 * pin the real behaviour with the reason it is right.
 */
class AdversarialInitTest {

  private static final Path AGENTS_MD = Path.of("AGENTS.md");
  private static final Path CLAUDE_MD = Path.of("CLAUDE.md");
  private static final Path DOCTOR_DIRECTORY = Path.of(".agents/skills/doctor");
  private static final Path DOCTOR_PAGE = Path.of(".agents/skills/doctor/SKILL.md");
  private static final Path VENDOR_DOCTOR_PAGE = Path.of(".claude/skills/doctor/SKILL.md");

  private Carrier carrier;

  @BeforeEach
  void openACarrier(@TempDir Path dir) {
    carrier = Carriers.fake(dir, "doctor");
  }

  private InitPlan plan(ProjectState state, InitOptions options) {
    return InitPlanner.plan(state, carrier, options);
  }

  private InitPlan plan(ProjectState state) {
    return plan(state, InitOptions.defaults());
  }

  private static Optional<Action> actionOn(InitPlan plan, Path path) {
    return plan.actions().stream().filter(action -> action.path().equals(path)).findFirst();
  }

  private static String after(InitPlan plan, Path path) {
    return ((FileEdit) actionOn(plan, path).orElseThrow()).after();
  }

  private static ProjectState.Builder project() {
    return ProjectState.builder();
  }

  // --- scope silences one half, the other half still decides ----------------------------------

  @Test
  void scopeSkillsLeavesBOTHContextFilesAloneEvenWhenEachWouldHaveHadAnOpinion() {
    ProjectState state =
        project().agentsMd("# Agents\n").claudeMd("# C\n").claudeDirectory(true).build();

    InitPlan plan = plan(state, InitOptions.defaults().withScope(Scope.SKILLS));

    assertThat(actionOn(plan, AGENTS_MD)).isEmpty();
    assertThat(actionOn(plan, CLAUDE_MD)).isEmpty();
    assertThat(actionOn(plan, DOCTOR_PAGE)).isPresent();
    assertThat(plan.hasRefusals())
        .as("the refusals belonged to the half that was silenced")
        .isFalse();
  }

  /**
   * The import line is part of the AGENTS.md half: it points AT that file, so it travels with it.
   */
  @Test
  void scopeAgentsMdCoversTheImportLineTooAndRefusesBothWithoutTheFlag() {
    ProjectState state =
        project().agentsMd("# Agents\n").claudeMd("# C\n").claudeDirectory(true).build();

    InitPlan plan = plan(state, InitOptions.defaults().withScope(Scope.AGENTS_MD));

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(Refuse.class);
    assertThat(actionOn(plan, CLAUDE_MD)).get().isInstanceOf(Refuse.class);
    assertThat(actionOn(plan, DOCTOR_PAGE)).isEmpty();
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  // --- one flag answers one question; the others still stand ----------------------------------

  /**
   * {@code --force} is about a skill directory somebody else owns. It says nothing about a context
   * file, so the vendor file this project already has is still refused in the same plan — and the
   * run exits 1 even though the forced half succeeded.
   */
  @Test
  void forceOverwritesAForeignSkillWhileTheVendorContextFileIsStillRefused() {
    ProjectState state = project().claudeMd("# C\n").addInstalledSkill(foreign("doctor")).build();

    InitPlan plan = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(actionOn(plan, CLAUDE_MD)).get().isInstanceOf(Refuse.class);
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  @Test
  void forceReachesTheVendorFlavourOfTheSameForeignSkillToo() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(foreign("doctor"))
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.CLAUDE, "doctor", Presence.FOREIGN, "", "theirs\n"))
            .build();

    InitPlan plan = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(actionOn(plan, VENDOR_DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
  }

  /** One skill of ours, one somebody else's: the refusal is per skill, never per run. */
  @Test
  void refusesOneSkillAndInstallsTheOtherInTheSamePlan(@TempDir Path dir) {
    carrier = Carriers.fake(dir.resolve("two"), "doctor", "clarity");
    ProjectState state = project().addInstalledSkill(foreign("doctor")).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, DOCTOR_DIRECTORY)).get().isInstanceOf(Refuse.class);
    assertThat(actionOn(plan, Path.of(".agents/skills/clarity/SKILL.md")))
        .get()
        .isInstanceOf(CreateFile.class);
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  /**
   * A skill of OURS whose page has drifted — hand-edited, or left by an older carrier — is
   * rewritten without any flag. Ours is ours: the flag exists for directories that are not.
   */
  @Test
  void rewritesOurOwnSkillPageWhenItHasDriftedFromTheCarrier() {
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", Carriers.FAKE_COORDINATE, "hand edited\n"))
            .build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, DOCTOR_PAGE))
        .isEqualTo(
            Provenance.stamp(
                Carriers.body("doctor", SkillFlavour.AGENTS), Carriers.FAKE_COORDINATE));
  }

  @Test
  void anUpgradeLeavesNoTraceOfTheCarrierItReplaced() {
    String old = "ai.narrativetrace:narrativetrace-skills:0.0.1";
    ProjectState state =
        project()
            .addInstalledSkill(ours("doctor", old, Provenance.stamp("---\nx\n---\nold\n", old)))
            .build();

    String content = after(plan(state), DOCTOR_PAGE);

    assertThat(Provenance.coordinateIn(content)).hasValue(Carriers.FAKE_COORDINATE);
    assertThat(content).doesNotContain(old);
  }

  // --- a dry run decides everything and writes nothing -----------------------------------------

  @Test
  void aDryRunStillRefusesWhatARealRunWouldRefuseAndStillExitsZero() {
    ProjectState state = project().agentsMd("# Agents\n").claudeMd("# C\n").build();

    InitPlan plan = plan(state, InitOptions.defaults().withDryRun(true));

    assertThat(plan.refusals()).hasSize(2);
    assertThat(plan.exitCode()).isZero();
    assertThat(actionOn(plan, DOCTOR_PAGE)).as("the rest of the plan is still decided").isPresent();
  }

  // --- what a file's shape does to the block ---------------------------------------------------

  @Test
  void appendingToAWindowsFileLeavesNoUnixLineEndingBehind() {
    ProjectState state = project().agentsMd("# Agents\r\n").build();

    String after = after(plan(state, InitOptions.defaults().withWriteExisting(true)), AGENTS_MD);

    assertThat(after).startsWith("# Agents\r\n\r\n").doesNotContain("\n\n").contains("\r\n\r\n");
  }

  @Test
  void aCreatedFileUsesUnixLineEndingsBecauseThereIsNoFileToFollow() {
    assertThat(after(plan(project().build()), AGENTS_MD)).endsWith("\n").doesNotContain("\r");
  }

  /** The created note marks a file the installer MADE — never one it merely wrote into. */
  @Test
  void onlyACreatedFileCarriesTheCreatedNote() {
    ProjectState empty = project().build();
    ProjectState existing = project().agentsMd("").build();

    InitPlan created = plan(empty);
    InitPlan appended = plan(existing, InitOptions.defaults().withWriteExisting(true));

    assertThat(actionOn(created, AGENTS_MD)).get().isInstanceOf(CreateFile.class);
    assertThat(after(created, AGENTS_MD)).contains(MarkedBlock.CREATED_NOTE);
    assertThat(actionOn(appended, AGENTS_MD)).get().isInstanceOf(AppendBlock.class);
    assertThat(after(appended, AGENTS_MD))
        .doesNotContain(MarkedBlock.CREATED_NOTE)
        .doesNotStartWith("\n");
  }

  @Test
  void refusesAVendorRuleFileWithTwoSectionsAndSaysToLeaveOne() {
    String twice =
        "<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
            + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n";
    ProjectState state = project().markedRuleFile(".cursorrules", twice).build();

    Refuse refusal = (Refuse) actionOn(plan(state), Path.of(".cursorrules")).orElseThrow();

    assertThat(refusal.reason())
        .contains("leave exactly one")
        .contains("line 1")
        .contains("line 4");
  }

  @Test
  void aVendorChoiceOfOffSurvivesEveryOtherFlag() {
    ProjectState state = project().claudeDirectory(true).claudeMd("# C\n").build();
    InitOptions everythingElse =
        InitOptions.defaults().withVendorClaude(Vendor.OFF).withForce(true).withWriteExisting(true);

    InitPlan plan = plan(state, everythingElse);

    assertThat(actionOn(plan, VENDOR_DOCTOR_PAGE)).isEmpty();
    assertThat(actionOn(plan, CLAUDE_MD)).as("the import line is not a vendor SKILL").isPresent();
  }

  private static InstalledSkill ours(String name, String coordinate, String body) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, Presence.OURS, coordinate, body);
  }

  private static InstalledSkill foreign(String name) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, Presence.FOREIGN, "", "theirs\n");
  }
}
