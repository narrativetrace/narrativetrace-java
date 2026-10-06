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

import ai.narrativetrace.tooling.init.Action.AdoptPage;
import ai.narrativetrace.tooling.init.Action.AppendBlock;
import ai.narrativetrace.tooling.init.Action.AppendLine;
import ai.narrativetrace.tooling.init.Action.CreateFile;
import ai.narrativetrace.tooling.init.Action.FileEdit;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.Action.ReplaceLink;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.init.InitOptions.Vendor;
import ai.narrativetrace.tooling.init.InstalledSkill.Presence;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every row of the existing-file policy, one case each, plus the near misses that decide whether a
 * row applies at all. The planner is pure, so none of this touches a disk: a snapshot is built by
 * hand and the plan is read back.
 */
class InitPlannerTest {

  private static final Path AGENTS_MD = Path.of("AGENTS.md");
  private static final Path CLAUDE_MD = Path.of("CLAUDE.md");
  private static final Path DOCTOR_PAGE = Path.of(".agents/skills/doctor/SKILL.md");
  private static final Path VENDOR_PAGE = Path.of(".claude/skills/doctor/SKILL.md");

  private Carrier carrier;

  @BeforeEach
  void openACarrier(@TempDir Path dir) {
    carrier = Carriers.fake(dir, "doctor");
  }

  private InitPlan plan(ProjectState state) {
    return InitPlanner.plan(state, carrier, InitOptions.defaults());
  }

  private InitPlan plan(ProjectState state, InitOptions options) {
    return InitPlanner.plan(state, carrier, options);
  }

  private static Optional<Action> actionOn(InitPlan plan, Path path) {
    return plan.actions().stream().filter(action -> action.path().equals(path)).findFirst();
  }

  private static String after(InitPlan plan, Path path) {
    return ((FileEdit) actionOn(plan, path).orElseThrow()).after();
  }

  private String block(ProjectState state) {
    return AgentsMdBlock.render(carrier, state);
  }

  private static ProjectState.Builder project() {
    return ProjectState.builder();
  }

  private static InitOptions writeExisting() {
    return InitOptions.defaults().withWriteExisting(true);
  }

  // --- AGENTS.md ------------------------------------------------------------------------------

  @Test
  void createsAgentsMdWithTheBlockAndTheCreatedNoteWhenThereIsNone() {
    ProjectState state = project().build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(CreateFile.class);
    assertThat(after(plan, AGENTS_MD))
        .isEqualTo("<!-- narrativetrace:created -->\n" + block(state));
  }

  @Test
  void refusesAnExistingAgentsMdWithoutTheFlagAndNamesTheFlag() {
    InitPlan plan = plan(project().agentsMd("# Agents\n").build());

    assertThat(actionOn(plan, AGENTS_MD))
        .get()
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Refuse.class))
        .extracting(Refuse::reason)
        .asString()
        .contains("--write-existing");
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  @Test
  void appendsToAnExistingAgentsMdWithTheFlag() {
    ProjectState state = project().agentsMd("# Agents\n").build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(AppendBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo("# Agents\n\n" + block(state));
  }

  @Test
  void replacesOurOwnBlockWithoutAnyFlag() {
    String old =
        "# Agents\n\n<!-- narrativetrace:start ai.narrativetrace:narrativetrace-skills:0.0.1 -->\n"
            + "stale\n<!-- narrativetrace:end -->\n\ntail\n";
    ProjectState state = project().agentsMd(old).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo("# Agents\n\n" + block(state) + "\ntail\n");
  }

  @Test
  void plansNothingForAnAgentsMdThatIsAlreadyCurrent() {
    ProjectState first = project().build();
    String written = "<!-- narrativetrace:created -->\n" + block(first);
    ProjectState state = project().agentsMd(written).build();

    assertThat(actionOn(plan(state), AGENTS_MD)).isEmpty();
  }

  @Test
  void refusesAnAgentsMdWithTwoBlocksAndNamesTheLines() {
    String twice =
        "<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
            + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n";

    InitPlan plan = plan(project().agentsMd(twice).build(), writeExisting());

    Refuse refusal = (Refuse) actionOn(plan, AGENTS_MD).orElseThrow();
    assertThat(refusal.reason()).contains("line 1").contains("line 4");
  }

  @Test
  void refusesAnAgentsMdWithAMarkerThatNeverCloses() {
    InitPlan plan =
        plan(project().agentsMd("<!-- narrativetrace:start -->\nbody\n").build(), writeExisting());

    assertThat(((Refuse) actionOn(plan, AGENTS_MD).orElseThrow()).reason()).contains("line 1");
  }

  @Test
  void theRestOfThePlanProceedsWhenAgentsMdIsRefused() {
    String twice =
        "<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
            + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n";

    InitPlan plan = plan(project().agentsMd(twice).build());

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(CreateFile.class);
    assertThat(plan.hasRefusals()).isTrue();
  }

  @Test
  void doesNotMistakeAMarkerInsideAFencedBlockForOurs() {
    String documented =
        "# Agents\n\n```\n<!-- narrativetrace:start -->\n<!-- narrativetrace:end -->\n```\n";
    ProjectState state = project().agentsMd(documented).build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(AppendBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo(documented + "\n" + block(state));
  }

  @Test
  void writesTheBlockWithTheFilesOwnLineEndings() {
    ProjectState state = project().agentsMd("# Agents\r\n").build();

    String after = after(plan(state, writeExisting()), AGENTS_MD);

    assertThat(after).isEqualTo("# Agents\r\n\r\n" + MarkedBlock.withEol(block(state), "\r\n"));
    assertThat(after).doesNotContain("\r\r");
  }

  @Test
  void keepsAByteOrderMarkAndAMissingFinalNewlineInMind() {
    ProjectState state = project().agentsMd("﻿# Agents").build();

    assertThat(after(plan(state, writeExisting()), AGENTS_MD))
        .isEqualTo("﻿# Agents\n\n" + block(state));
  }

  @Test
  void appendsToAnEmptyButExistingAgentsMdWithTheFlag() {
    ProjectState state = project().agentsMd("").build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(actionOn(plan, AGENTS_MD)).get().isInstanceOf(AppendBlock.class);
    assertThat(after(plan, AGENTS_MD)).isEqualTo(block(state));
  }

  /**
   * Found by the generative round trip: a block appended after an unfinished fence lands INSIDE it,
   * where the next run's scan cannot see its own markers — so the run after that appends a second
   * copy, and so on. Refusing is the only safe answer; closing somebody's fence for them is not.
   */
  @Test
  void refusesToAppendToAnAgentsMdThatEndsInsideAnUnfinishedFence() {
    ProjectState state = project().agentsMd("# Agents\n\n```\nnot closed\n").build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(((Refuse) actionOn(plan, AGENTS_MD).orElseThrow()).reason())
        .contains("unfinished fenced code block");
  }

  @Test
  void refusesToAddTheImportLineToAVendorFileThatEndsInsideAnUnfinishedFence() {
    ProjectState state = project().claudeMd("# Project\n\n~~~\nnot closed\n").build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(((Refuse) actionOn(plan, CLAUDE_MD).orElseThrow()).reason())
        .contains("unfinished fenced code block");
  }

  @Test
  void stillReplacesOurOwnSectionInAFileWhoseFenceOpensAfterIt() {
    String text =
        "<!-- narrativetrace:start -->\nstale\n<!-- narrativetrace:end -->\n\n```\nnot closed\n";
    ProjectState state = project().agentsMd(text).build();

    assertThat(actionOn(plan(state), AGENTS_MD)).get().isInstanceOf(ReplaceBlock.class);
  }

  // --- CLAUDE.md ------------------------------------------------------------------------------

  @Test
  void createsNoVendorContextFileWhenTheProjectHasNone() {
    assertThat(actionOn(plan(project().build()), CLAUDE_MD)).isEmpty();
  }

  @Test
  void appendsTheImportLineToAnExistingVendorContextFileWithTheFlag() {
    ProjectState state = project().claudeMd("# Project\n").build();

    InitPlan plan = plan(state, writeExisting());

    assertThat(actionOn(plan, CLAUDE_MD)).get().isInstanceOf(AppendLine.class);
    assertThat(after(plan, CLAUDE_MD)).isEqualTo("# Project\n\n@AGENTS.md\n");
  }

  @Test
  void refusesToTouchTheVendorContextFileWithoutTheFlag() {
    InitPlan plan = plan(project().claudeMd("# Project\n").build());

    assertThat(((Refuse) actionOn(plan, CLAUDE_MD).orElseThrow()).reason())
        .contains("--write-existing");
  }

  @Test
  void leavesAVendorContextFileThatAlreadyImportsAlone() {
    ProjectState state = project().claudeMd("# Project\n\n@AGENTS.md\n").build();

    assertThat(actionOn(plan(state, writeExisting()), CLAUDE_MD)).isEmpty();
  }

  @Test
  void countsAnImportLineWithTrailingSpacesAsAlreadyThere() {
    ProjectState state = project().claudeMd("# Project\n\n@AGENTS.md   \n").build();

    assertThat(actionOn(plan(state, writeExisting()), CLAUDE_MD)).isEmpty();
  }

  @Test
  void doesNotCountAnImportLineInsideACommentOrAFence() {
    ProjectState commented = project().claudeMd("<!-- @AGENTS.md -->\n").build();
    ProjectState fenced = project().claudeMd("```\n@AGENTS.md\n```\n").build();

    assertThat(actionOn(plan(commented, writeExisting()), CLAUDE_MD))
        .get()
        .isInstanceOf(AppendLine.class);
    assertThat(actionOn(plan(fenced, writeExisting()), CLAUDE_MD))
        .get()
        .isInstanceOf(AppendLine.class);
  }

  // --- skills ---------------------------------------------------------------------------------

  @Test
  void copiesEverySkillIntoTheOpenStandardPathWithItsProvenanceLine() {
    InitPlan plan = plan(project().build());

    assertThat(after(plan, DOCTOR_PAGE))
        .isEqualTo(
            Provenance.stamp(
                Carriers.body("doctor", SkillFlavour.AGENTS), Carriers.FAKE_COORDINATE));
    assertThat(Provenance.coordinateIn(after(plan, DOCTOR_PAGE)))
        .hasValue(Carriers.FAKE_COORDINATE);
  }

  @Test
  void installsTheVendorFlavourWhereTheVendorIsDetected() {
    Path vendorPage = Path.of(".claude/skills/doctor/SKILL.md");

    assertThat(actionOn(plan(project().claudeDirectory(true).build()), vendorPage)).isPresent();
    assertThat(actionOn(plan(project().claudeMd("# C\n").build()), vendorPage)).isPresent();
    assertThat(actionOn(plan(project().build()), vendorPage)).isEmpty();
  }

  @Test
  void obeysAnExplicitVendorChoiceOverDetection() {
    Path vendorPage = Path.of(".claude/skills/doctor/SKILL.md");
    InitOptions on = InitOptions.defaults().withVendorClaude(Vendor.ON);
    InitOptions off = InitOptions.defaults().withVendorClaude(Vendor.OFF);

    assertThat(actionOn(plan(project().build(), on), vendorPage)).isPresent();
    assertThat(actionOn(plan(project().claudeDirectory(true).build(), off), vendorPage)).isEmpty();
  }

  @Test
  void plansNothingForASkillThatIsAlreadyExactlyRight() {
    String installed =
        Provenance.stamp(Carriers.body("doctor", SkillFlavour.AGENTS), Carriers.FAKE_COORDINATE);
    ProjectState state =
        project().addInstalledSkill(ours("doctor", Carriers.FAKE_COORDINATE, installed)).build();

    assertThat(plan(state).actions()).noneMatch(action -> action.path().equals(DOCTOR_PAGE));
  }

  @Test
  void upgradesASkillInstalledFromAnOlderCarrierWithoutAnyFlag() {
    String older =
        Provenance.stamp(
            "---\nname: doctor\n---\nold\n", "ai.narrativetrace:narrativetrace-skills:0.0.1");
    ProjectState state =
        project()
            .addInstalledSkill(
                ours("doctor", "ai.narrativetrace:narrativetrace-skills:0.0.1", older))
            .build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(Provenance.coordinateIn(after(plan, DOCTOR_PAGE)))
        .hasValue(Carriers.FAKE_COORDINATE);
  }

  @Test
  void refusesASkillDirectorySomebodyElseOwnsAndLetsTheOthersProceed() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.FOREIGN, "", "theirs\n"))
            .build();

    InitPlan plan = plan(state);

    Refuse refusal = (Refuse) actionOn(plan, Path.of(".agents/skills/doctor")).orElseThrow();
    assertThat(refusal.reason()).contains("--force");
    assertThat(actionOn(plan, AGENTS_MD)).isPresent();
  }

  @Test
  void overwritesAForeignSkillDirectoryWhenForced() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.FOREIGN, "", "theirs\n"))
            .build();

    InitPlan plan = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(ReplaceBlock.class);
  }

  @Test
  void createsThePageWhenAForeignDirectoryHasNoneAndForceIsGiven() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.FOREIGN, "", ""))
            .build();

    InitPlan plan = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(CreateFile.class);
  }

  @Test
  void refusesAFileSittingWhereASkillDirectoryBelongsEvenWhenForced() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.NOT_A_DIRECTORY, "", ""))
            .build();

    InitPlan plan = plan(state, InitOptions.defaults().withForce(true));

    assertThat(((Refuse) actionOn(plan, Path.of(".agents/skills/doctor")).orElseThrow()).reason())
        .contains("not a directory");
  }

  // --- adoption of an identical page ----------------------------------------------------------

  @Test
  void adoptsAForeignPageThatIsIdenticalToTheCarriersOwnWithoutAnyFlag() {
    ProjectState state = project().addInstalledSkill(theirs(identicalPage())).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(AdoptPage.class);
    assertThat(after(plan, DOCTOR_PAGE))
        .isEqualTo(Provenance.stamp(identicalPage(), Carriers.FAKE_COORDINATE));
    assertThat(plan.exitCode()).isZero();
  }

  /**
   * The vendor flavour is adopted on the same terms: a rule that holds on one path only is none.
   */
  @Test
  void adoptsAnIdenticalVendorPageToo() {
    Path vendorPage = Path.of(".claude/skills/doctor/SKILL.md");
    String rendered = Carriers.body("doctor", SkillFlavour.CLAUDE);
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(
                new InstalledSkill(SkillFlavour.CLAUDE, "doctor", Presence.FOREIGN, "", rendered))
            .build();

    assertThat(actionOn(plan(state), vendorPage)).get().isInstanceOf(AdoptPage.class);
  }

  /**
   * The OTHER flavour's page at this flavour's path is a near miss, not a match: it is what a
   * registry leaves when it installs one flavour and links the other to it, and adopting it would
   * leave the project with two copies of the open-standard page and no vendor page at all.
   */
  @Test
  void refusesAPageThatIsTheOtherFlavoursRendering() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.CLAUDE,
                    "doctor",
                    Presence.FOREIGN,
                    "",
                    Carriers.body("doctor", SkillFlavour.AGENTS)))
            .build();

    assertThat(actionOn(plan(state), Path.of(".claude/skills/doctor")))
        .get()
        .isInstanceOf(Refuse.class);
  }

  /**
   * Adoption is about a name the catalogue owns; a skill of theirs stays theirs, identical or not.
   */
  @Test
  void leavesAnIdenticalPageUnderASkillNameTheCatalogueNeverNamedAlone() {
    ProjectState state =
        project()
            .addInstalledSkill(
                new InstalledSkill(
                    SkillFlavour.AGENTS,
                    "deploy-to-staging",
                    Presence.FOREIGN,
                    "",
                    identicalPage()))
            .build();

    assertThat(plan(state).actions())
        .noneMatch(action -> action.path().startsWith(".agents/skills/deploy-to-staging"));
  }

  @Test
  void needsNoForceToAdoptAndAdoptsWhenForcedAnyway() {
    ProjectState state = project().addInstalledSkill(theirs(identicalPage())).build();

    InitPlan forced = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(forced, DOCTOR_PAGE)).get().isInstanceOf(AdoptPage.class);
  }

  /** Once adopted, the page is ours: the next run of the same carrier plans nothing at all. */
  @Test
  void plansNothingForAPageItAlreadyAdopted() {
    ProjectState adopted =
        project()
            .addInstalledSkill(
                ours(
                    "doctor",
                    Carriers.FAKE_COORDINATE,
                    Provenance.stamp(identicalPage(), Carriers.FAKE_COORDINATE)))
            .build();

    assertThat(actionOn(plan(adopted), DOCTOR_PAGE)).isEmpty();
  }

  // --- symbolic links -------------------------------------------------------------------------

  /**
   * The registry hazard, at the planner: the vendor directory is a LINK to the open-standard one,
   * so writing the vendor page through it would overwrite the page just adopted. The link is
   * replaced by a real directory instead, and the page it pointed at is not in the plan at all.
   */
  @Test
  void replacesALinkOverAPageItAdoptsWithARealDirectory() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, identicalPage()))
            .build();

    InitPlan plan = plan(state);

    ReplaceLink action = (ReplaceLink) actionOn(plan, VENDOR_PAGE).orElseThrow();
    assertThat(action.link().toString()).isEqualTo(".claude/skills/doctor");
    assertThat(action.target()).isEqualTo("../../.agents/skills/doctor");
    assertThat(action.after())
        .isEqualTo(
            Provenance.stamp(
                Carriers.body("doctor", SkillFlavour.CLAUDE), Carriers.FAKE_COORDINATE));
    assertThat(plan.exitCode()).isZero();
  }

  @Test
  void replacesALinkOverAPageOfItsOwn() {
    String ours =
        Provenance.stamp(identicalPage(), "ai.narrativetrace:narrativetrace-skills:0.0.1");
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, ours))
            .build();

    assertThat(actionOn(plan(state), VENDOR_PAGE)).get().isInstanceOf(ReplaceLink.class);
  }

  /** A linked PAGE inside a real directory is the same decision, one level down. */
  @Test
  void replacesALinkedPageWithARealFile() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_PAGE, identicalPage()))
            .build();

    ReplaceLink action = (ReplaceLink) actionOn(plan(state), VENDOR_PAGE).orElseThrow();
    assertThat(action.link()).isEqualTo(VENDOR_PAGE);
  }

  @Test
  void refusesALinkOverAPageNobodyCanPlaceAndNamesTheTarget() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, "somebody else's page\n"))
            .build();

    Refuse refusal = (Refuse) actionOn(plan(state), Path.of(".claude/skills/doctor")).orElseThrow();
    assertThat(refusal.reason())
        .contains("symbolic link")
        .contains("../../.agents/skills/doctor")
        .contains("remove the link");
  }

  /** A dangling link, and one out of the project, both reach no page: refused, never followed. */
  @Test
  void refusesALinkThatReachesNoPage() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, ""))
            .build();

    Refuse refusal = (Refuse) actionOn(plan(state), Path.of(".claude/skills/doctor")).orElseThrow();
    assertThat(refusal.reason()).contains("no page").contains("../../.agents/skills/doctor");
  }

  /**
   * The refusal for a linked PAGE names the page, not the directory. One level down from {@link
   * #refusesALinkOverAPageNobodyCanPlaceAndNamesTheTarget}, and a different path in the plan —
   * which is what the "never two actions on one path" property reads, and what a person has to be
   * told to remove.
   */
  @Test
  void refusesALinkedPageOverSomebodyElsesPageAtThePageItself() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_PAGE, "somebody else's page\n"))
            .build();

    Refuse refusal = (Refuse) actionOn(plan(state), VENDOR_PAGE).orElseThrow();
    assertThat(refusal.reason()).contains("symbolic link").contains("--force covers content");
    assertThat(plan(state).exitCode()).isEqualTo(1);
  }

  /** {@code --force} covers foreign CONTENT, never structure — and a link is structure. */
  @Test
  void forceDoesNotCoverALink() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, "somebody else's page\n"))
            .build();

    InitPlan forced = plan(state, InitOptions.defaults().withForce(true));

    assertThat(actionOn(forced, Path.of(".claude/skills/doctor"))).get().isInstanceOf(Refuse.class);
    assertThat(forced.exitCode()).isEqualTo(1);
  }

  /** One refused link never costs the other flavour, or the other skill, its own install. */
  @Test
  void aRefusedLinkLeavesTheRestOfThePlanAlone() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .addInstalledSkill(linked(Presence.LINKED_DIRECTORY, "theirs\n"))
            .build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(CreateFile.class);
    assertThat(actionOn(plan, AGENTS_MD)).isPresent();
  }

  /**
   * A linked install root is one decision, not one per skill: the refusal names the root, every
   * skill of that flavour is left unplanned, and the other flavour is installed as usual.
   */
  @Test
  void refusesAWholeFlavourWhoseInstallRootIsALinkAndInstallsTheOther() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .linkedInstallRoot(SkillFlavour.CLAUDE, "/elsewhere")
            .build();

    InitPlan plan = plan(state);

    Refuse refusal = (Refuse) actionOn(plan, Path.of(".claude/skills")).orElseThrow();
    assertThat(refusal.reason())
        .contains("symbolic link")
        .contains("/elsewhere")
        .contains("remove the link");
    assertThat(actionOn(plan, VENDOR_PAGE)).as("no page is planned behind the link").isEmpty();
    assertThat(actionOn(plan, DOCTOR_PAGE)).get().isInstanceOf(CreateFile.class);
    assertThat(plan.exitCode()).isEqualTo(1);
  }

  // --- scope, rule files and the plan itself --------------------------------------------------

  @Test
  void plansOnlyTheHalfItWasAskedFor() {
    ProjectState state = project().build();

    InitPlan skillsOnly = plan(state, InitOptions.defaults().withScope(Scope.SKILLS));
    InitPlan sectionOnly = plan(state, InitOptions.defaults().withScope(Scope.AGENTS_MD));

    assertThat(actionOn(skillsOnly, DOCTOR_PAGE)).isPresent();
    assertThat(actionOn(skillsOnly, AGENTS_MD)).isEmpty();
    assertThat(actionOn(sectionOnly, DOCTOR_PAGE)).isEmpty();
    assertThat(actionOn(sectionOnly, AGENTS_MD)).isPresent();
  }

  @Test
  void keepsAnExistingBlockInAVendorRuleFileUpToDate() {
    String rules = "# rules\n\n<!-- narrativetrace:start -->\nstale\n<!-- narrativetrace:end -->\n";
    ProjectState state = project().markedRuleFile(".cursorrules", rules).build();

    InitPlan plan = plan(state);

    assertThat(actionOn(plan, Path.of(".cursorrules"))).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, Path.of(".cursorrules"))).contains(block(state));
  }

  @Test
  void refusesAVendorRuleFileWithTwoBlocks() {
    String rules =
        "<!-- narrativetrace:start -->\na\n<!-- narrativetrace:end -->\n"
            + "<!-- narrativetrace:start -->\nb\n<!-- narrativetrace:end -->\n";
    ProjectState state = project().markedRuleFile(".cursorrules", rules).build();

    assertThat(actionOn(plan(state), Path.of(".cursorrules"))).get().isInstanceOf(Refuse.class);
  }

  @Test
  void stampsThePlanWithTheCarrierAndCarriesTheDryRunFlag() {
    InitPlan plan = plan(project().build(), InitOptions.defaults().withDryRun(true));

    assertThat(plan.carrier()).isEqualTo(Carriers.FAKE_COORDINATE);
    assertThat(plan.dryRun()).isTrue();
    assertThat(plan.exitCode()).isZero();
  }

  @Test
  void neverPlansTwoActionsOnOnePath() {
    ProjectState state =
        project()
            .claudeDirectory(true)
            .claudeMd("# C\n")
            .agentsMd("# A\n")
            .markedRuleFile(
                ".cursorrules", "<!-- narrativetrace:start -->\n<!-- narrativetrace:end -->\n")
            .build();

    List<Path> paths = plan(state, writeExisting()).actions().stream().map(Action::path).toList();

    assertThat(paths).doesNotHaveDuplicates();
  }

  @Test
  void refusesToPlanWithoutASnapshotACarrierOrOptions() {
    assertThatThrownBy(() -> InitPlanner.plan(null, carrier, InitOptions.defaults()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InitPlanner.plan(project().build(), null, InitOptions.defaults()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InitPlanner.plan(project().build(), carrier, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** The carrier's own AGENTS page, byte for byte — what a registry install leaves behind. */
  private static String identicalPage() {
    return Carriers.body("doctor", SkillFlavour.AGENTS);
  }

  private static InstalledSkill theirs(String body) {
    return new InstalledSkill(SkillFlavour.AGENTS, "doctor", Presence.FOREIGN, "", body);
  }

  /** A skill whose vendor path is what `npx skills add` leaves: a link to the agents directory. */
  private static InstalledSkill linked(Presence presence, String reached) {
    return new InstalledSkill(
        SkillFlavour.CLAUDE, "doctor", presence, "", reached, "../../.agents/skills/doctor");
  }

  private static InstalledSkill ours(String name, String coordinate, String body) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, Presence.OURS, coordinate, body);
  }
}
