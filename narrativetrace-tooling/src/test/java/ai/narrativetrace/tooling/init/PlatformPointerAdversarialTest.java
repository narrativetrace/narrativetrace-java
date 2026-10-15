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

import ai.narrativetrace.tooling.platforms.PlatformTable;
import ai.narrativetrace.tooling.platforms.Pointer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adversarial round trips and edge cases for the platform pointers on a real temporary project:
 * CRLF and byte-order-marked files, a second install, uninstall after install, and links.
 */
class PlatformPointerAdversarialTest {

  private static final String BOM = "﻿";
  private static final String REPLIT_MD = "replit.md";
  private static final String CURSOR_RULE = ".cursor/rules/narrativetrace.mdc";
  private static final String CLAUDE_MD = "CLAUDE.md";

  @TempDir Path root;

  private Path project;
  private Carrier carrier;

  @BeforeEach
  void setUp() throws IOException {
    project = Files.createDirectories(root.resolve("project"));
    carrier = Carriers.fake(root, "doctor");
  }

  // --- helpers ----------------------------------------------------------------------------------

  private void write(String relative, String content) throws IOException {
    Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private String read(String relative) throws IOException {
    return Files.readString(project.resolve(relative));
  }

  private InitPlan planInstall(InitOptions options) {
    return InitPlanner.plan(ProjectStateReader.read(project), carrier, options);
  }

  private InitPlan planUninstall() {
    return UninstallPlanner.plan(ProjectStateReader.read(project), InitOptions.defaults());
  }

  private void apply(InitPlan plan) {
    PlanExecutor.execute(plan, project);
  }

  private String cursorText() {
    return ((Pointer.Rule) PlatformTable.row("cursor").orElseThrow().pointer()).text();
  }

  private String section() {
    return AgentsMdBlock.render(carrier, ProjectStateReader.read(project));
  }

  private static String crlf(String text) {
    return text.replace("\n", "\r\n");
  }

  // --- round trips: install then uninstall ------------------------------------------------------

  @Test
  void installThenUninstallLeavesACrlfReplitMdByteIdentical() throws IOException {
    write(".replit", "run = \"x\"\n");
    write(REPLIT_MD, "# Notes\r\n\r\nHello\r\n");
    Map<String, String> before = Projects.snapshotOf(project);

    apply(planInstall(InitOptions.defaults().withWriteExisting(true)));
    apply(planUninstall());

    assertThat(Projects.snapshotOf(project)).isEqualTo(before);
  }

  @Test
  void installThenUninstallLeavesAByteOrderMarkedReplitMdByteIdentical() throws IOException {
    write(".replit", "run = \"x\"\n");
    write(REPLIT_MD, BOM + "# Notes\n");
    Map<String, String> before = Projects.snapshotOf(project);

    apply(planInstall(InitOptions.defaults().withWriteExisting(true)));
    apply(planUninstall());

    assertThat(Projects.snapshotOf(project)).isEqualTo(before);
  }

  @Test
  void aFileWithoutAFinalNewlineComesBackWithOneNewlineAndNothingElse() throws IOException {
    // Pinned limitation, documented on MarkedBlock: the one newline an append adds to a file that
    // had none is not taken back, because a block must start on its own line.
    write(".replit", "x\n");
    write(REPLIT_MD, "# Notes");

    apply(planInstall(InitOptions.defaults().withWriteExisting(true)));
    apply(planUninstall());

    assertThat(read(REPLIT_MD)).isEqualTo("# Notes\n");
  }

  @Test
  void installThenUninstallRestoresAWholeMixedProjectByteForByte() throws IOException {
    write(".replit", "x\n");
    write(CLAUDE_MD, "# Claude\n");
    write(REPLIT_MD, crlf("# Notes\n\nHello\n"));
    Files.createDirectories(project.resolve(".cursor"));
    Map<String, String> before = Projects.snapshotOf(project);

    apply(planInstall(InitOptions.defaults().withWriteExisting(true)));
    apply(planUninstall());

    assertThat(Projects.snapshotOf(project)).isEqualTo(before);
  }

  @Test
  void aSecondInstallOverAFullyInstalledMixedProjectPlansNothing() throws IOException {
    write(".replit", "x\n");
    write(CLAUDE_MD, "# Claude\n");
    write(REPLIT_MD, crlf("# Notes\n"));
    Files.createDirectories(project.resolve(".cursor"));
    apply(planInstall(InitOptions.defaults().withWriteExisting(true)));

    assertThat(planInstall(InitOptions.defaults().withWriteExisting(true)).actions()).isEmpty();
  }

  @Test
  void uninstallKeepsTheUsersNotesWrittenBelowACreatedReplitMd() throws IOException {
    write(".replit", "x\n");
    apply(planInstall(InitOptions.defaults()));
    write(REPLIT_MD, read(REPLIT_MD) + "My notes\n");

    apply(planUninstall());

    assertThat(read(REPLIT_MD)).isEqualTo("My notes\n");
  }

  // --- CRLF Cursor rule --------------------------------------------------------------------------

  @Test
  void aCrlfCursorRuleThatIsAlreadyCurrentIsNotRewritten() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    apply(planInstall(InitOptions.defaults()));
    write(CURSOR_RULE, crlf(read(CURSOR_RULE)));

    assertThat(planInstall(InitOptions.defaults()).actions()).isEmpty();
  }

  @Test
  void uninstallDeletesACrlfCursorRuleTheInstallerMade() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    apply(planInstall(InitOptions.defaults()));
    write(CURSOR_RULE, crlf(read(CURSOR_RULE)));

    apply(planUninstall());

    assertThat(project.resolve(CURSOR_RULE)).doesNotExist();
  }

  @Test
  void uninstallKeepsTheUsersCrlfLinesBelowACursorRuleSection() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    apply(planInstall(InitOptions.defaults()));
    write(CURSOR_RULE, crlf(read(CURSOR_RULE)) + "Custom line\r\n");

    apply(planUninstall());

    assertThat(read(CURSOR_RULE)).isEqualTo(crlf(CursorRule.FRONTMATTER) + "Custom line\r\n");
  }

  @Test
  void aCursorRuleSavedWithAByteOrderMarkIsDeletedWhenTheInstallerMadeIt() throws IOException {
    // An editor's byte-order mark must not defeat the frontmatter-only comparison, or the
    // installer's own file survives uninstall as a stub holding nothing but its frontmatter.
    Files.createDirectories(project.resolve(".cursor"));
    apply(planInstall(InitOptions.defaults()));
    write(CURSOR_RULE, BOM + read(CURSOR_RULE));

    apply(planUninstall());

    assertThat(project.resolve(CURSOR_RULE)).doesNotExist();
  }

  @Test
  void twoSectionsInACursorRuleAreRefusedAndTheFileIsLeftAlone() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    String rule = CursorRule.render(cursorText(), carrier.coordinate());
    write(CURSOR_RULE, rule + "\n" + rule);

    InitPlan plan = planInstall(InitOptions.defaults());
    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(CURSOR_RULE));
    apply(plan);

    assertThat(read(CURSOR_RULE)).isEqualTo(rule + "\n" + rule);
  }

  // --- a managed section that is not what it was ------------------------------------------------

  @Test
  void aStaleCrlfSectionInReplitMdIsRefreshedInCrlfAroundTheUsersText() throws IOException {
    write(".replit", "x\n");
    String current = crlf(section());
    String stale =
        current.replace(carrier.coordinate(), "ai.narrativetrace:narrativetrace-skills:0.0.1");
    write(REPLIT_MD, "# Notes\r\n\r\n" + stale);

    apply(planInstall(InitOptions.defaults()));

    assertThat(read(REPLIT_MD)).isEqualTo("# Notes\r\n\r\n" + current);
  }

  @Test
  void aByteOrderMarkBeforeTheSectionSurvivesARefreshOfThatSection() throws IOException {
    // A section on line one must not swallow the byte-order mark: the refresh changes the
    // section and nothing outside it.
    write(".replit", "x\n");
    String stale =
        section().replace(carrier.coordinate(), "ai.narrativetrace:narrativetrace-skills:0.0.1");
    write(REPLIT_MD, BOM + stale);

    apply(planInstall(InitOptions.defaults()));

    assertThat(read(REPLIT_MD)).startsWith(BOM);
  }

  // --- refusals that must not touch the file ----------------------------------------------------

  @Test
  void installRefusesAReplitMdWhoseSectionNeverClosesEvenWithWriteExisting() throws IOException {
    write(".replit", "x\n");
    String broken = "# Notes\n<!-- narrativetrace:start ai:x:1.2.3 -->\nbody\n";
    write(REPLIT_MD, broken);

    InitPlan plan = planInstall(InitOptions.defaults().withWriteExisting(true));
    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(REPLIT_MD));
    apply(plan);

    assertThat(read(REPLIT_MD)).isEqualTo(broken);
  }

  @Test
  void uninstallRefusesAReplitMdCarryingTwoSectionsAndLeavesItAlone() throws IOException {
    String twice = section() + "\n" + section();
    write(REPLIT_MD, twice);

    InitPlan plan = planUninstall();
    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(REPLIT_MD));
    apply(plan);

    assertThat(read(REPLIT_MD)).isEqualTo(twice);
  }

  // --- links ------------------------------------------------------------------------------------

  @Test
  void aReplitMdThatIsALinkOutOfTheProjectIsRefusedAndTheTargetIsNotWrittenThrough()
      throws IOException {
    write(".replit", "x\n");
    Path outside = Files.writeString(root.resolve("outside.md"), "# elsewhere\n");
    Files.createSymbolicLink(project.resolve(REPLIT_MD), outside);

    InitPlan plan = planInstall(InitOptions.defaults().withWriteExisting(true));
    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(REPLIT_MD));
    apply(plan);

    assertThat(Files.readString(outside)).isEqualTo("# elsewhere\n");
  }

  @Test
  void aCursorDirectoryThatIsALinkIsRefusedAndNothingIsWrittenBehindIt() throws IOException {
    Path elsewhere = Files.createDirectories(root.resolve("elsewhere"));
    Files.createSymbolicLink(project.resolve(".cursor"), elsewhere);

    InitPlan plan = planInstall(InitOptions.defaults());
    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(CURSOR_RULE));
    apply(plan);

    assertThat(elsewhere.resolve("rules")).doesNotExist();
  }

  // --- detection --------------------------------------------------------------------------------

  @Test
  void aReplitDirectoryMarksTheProjectExactlyLikeTheDotReplitFileDoes() throws IOException {
    Files.createDirectories(project.resolve(".replit"));

    InitPlan plan = planInstall(InitOptions.defaults());

    assertThat(plan.actions())
        .anyMatch(a -> a instanceof Action.CreateFile c && c.path().equals(Path.of(REPLIT_MD)));
  }

  /**
   * A directory where replit.md belongs, and a file where .cursor belongs, are planned as creates —
   * the planner reads regular files only — and the EXECUTOR reports the failed write as a refusal,
   * exits non-zero and leaves what was there untouched. Reporting at apply time is the installer's
   * existing contract for any path that will not cooperate.
   */
  @Test
  void aReplitMdThatIsADirectoryIsReportedRefusedByTheExecutorAndLeftIntact() throws IOException {
    Files.createDirectories(project.resolve(REPLIT_MD));

    ExecutionReport report = PlanExecutor.execute(planInstall(InitOptions.defaults()), project);

    assertThat(report.hasRefusals()).isTrue();
    assertThat(report.exitCode()).isNotZero();
    assertThat(project.resolve(REPLIT_MD)).isDirectory();
  }

  @Test
  void aCursorDirectoryThatIsAFileIsReportedRefusedByTheExecutorAndLeftIntact() throws IOException {
    write(".cursor", "not a directory\n");

    ExecutionReport report = PlanExecutor.execute(planInstall(InitOptions.defaults()), project);

    assertThat(report.hasRefusals()).isTrue();
    assertThat(read(".cursor")).isEqualTo("not a directory\n");
  }
}
