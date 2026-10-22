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

import ai.narrativetrace.tooling.init.Action.AdoptPage;
import ai.narrativetrace.tooling.init.Action.AppendBlock;
import ai.narrativetrace.tooling.init.Action.CreateFile;
import ai.narrativetrace.tooling.init.Action.FileEdit;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import ai.narrativetrace.tooling.platforms.PlatformTable;
import ai.narrativetrace.tooling.platforms.Pointer;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D2 — a platform's pointer is written only when the platform's marker is in the project, adopted
 * when it is already ours, refused when it is somebody's, and never written through a link.
 */
class PlatformPointerPlannerTest {

  private static final Path REPLIT_MD = Path.of("replit.md");
  private static final Path CURSOR_RULE = Path.of(".cursor/rules/narrativetrace.mdc");

  private Carrier carrier;

  @BeforeEach
  void openACarrier(@TempDir Path dir) {
    carrier = Carriers.fake(dir, "doctor");
  }

  private InitPlan plan(ProjectState state) {
    return InitPlanner.plan(state, carrier, InitOptions.defaults(), CommandRoute.CLI);
  }

  private static ProjectState.Builder project() {
    return ProjectState.builder();
  }

  private static Optional<Action> on(InitPlan plan, Path path) {
    return plan.actions().stream().filter(action -> action.path().equals(path)).findFirst();
  }

  private static String after(InitPlan plan, Path path) {
    return ((FileEdit) on(plan, path).orElseThrow()).after();
  }

  private String cursorRule(String coordinate) {
    Pointer.Rule rule = (Pointer.Rule) PlatformTable.row("cursor").orElseThrow().pointer();
    return CursorRule.render(rule.text(), coordinate);
  }

  private String section(ProjectState state) {
    return AgentsMdBlock.render(carrier, state, CommandRoute.CLI);
  }

  // --- no marker, no pointer --------------------------------------------------------------------

  @Test
  void writesNoPointerForAProjectOnNoPlatform() {
    InitPlan plan = plan(project().build());

    assertThat(on(plan, REPLIT_MD)).isEmpty();
    assertThat(on(plan, CURSOR_RULE)).isEmpty();
  }

  @Test
  void writesNoPointerForPlatformsThatNeedNone() {
    ProjectState state =
        project()
            .detectedPlatform("antigravity")
            .detectedPlatform("github-copilot")
            .detectedPlatform("claude-code")
            .build();

    assertThat(plan(state).actions())
        .extracting(a -> a.path().toString())
        .doesNotContain("replit.md", ".cursor/rules/narrativetrace.mdc");
  }

  // --- Replit -----------------------------------------------------------------------------------

  @Test
  void createsReplitMdWithTheManagedSectionWhenOnlyTheDotReplitFileMarksTheProject() {
    ProjectState state = project().detectedPlatform("replit").build();

    InitPlan plan = plan(state);

    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(CreateFile.class);
    assertThat(after(plan, REPLIT_MD)).isEqualTo(MarkedBlock.CREATED_NOTE + "\n" + section(state));
  }

  @Test
  void refusesToAppendToAnExistingReplitMdWithoutPermission() {
    ProjectState state =
        project().detectedPlatform("replit").pointerFile("replit.md", "# My notes\n").build();

    assertThat(on(plan(state), REPLIT_MD)).get().isInstanceOf(Refuse.class);
  }

  @Test
  void appendsTheSectionToAnExistingReplitMdWhenAllowed() {
    ProjectState state =
        project().detectedPlatform("replit").pointerFile("replit.md", "# My notes\n").build();

    InitPlan plan =
        InitPlanner.plan(
            state, carrier, InitOptions.defaults().withWriteExisting(true), CommandRoute.CLI);

    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(AppendBlock.class);
    assertThat(after(plan, REPLIT_MD)).startsWith("# My notes\n\n").endsWith(section(state));
  }

  @Test
  void leavesACurrentReplitMdAloneAndRefreshesAStaleOne() {
    ProjectState base = project().detectedPlatform("replit").build();
    String current = MarkedBlock.CREATED_NOTE + "\n" + section(base);
    ProjectState upToDate =
        project().detectedPlatform("replit").pointerFile("replit.md", current).build();
    assertThat(on(plan(upToDate), REPLIT_MD)).isEmpty();

    String stale =
        current.replace(carrier.coordinate(), "ai.narrativetrace:narrativetrace-skills:0.0.1");
    ProjectState behind =
        project().detectedPlatform("replit").pointerFile("replit.md", stale).build();

    assertThat(on(plan(behind), REPLIT_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan(behind), REPLIT_MD)).isEqualTo(current);
  }

  // --- Cursor -----------------------------------------------------------------------------------

  @Test
  void createsTheCursorRuleWhenTheCursorDirectoryIsThere() {
    ProjectState state = project().detectedPlatform("cursor").build();

    InitPlan plan = plan(state);

    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(CreateFile.class);
    assertThat(after(plan, CURSOR_RULE)).isEqualTo(cursorRule(carrier.coordinate()));
  }

  @Test
  void theCursorRuleIsOneRuleAndNotACopyOfAnySkillPage() {
    String rule = cursorRule(carrier.coordinate());

    assertThat(rule).startsWith("---\n").contains("alwaysApply: true").contains(".agents/skills");
    assertThat(rule).doesNotContain("narrativetrace-doctor");
    assertThat(MarkedBlock.scan(rule).hasExactlyOneRegion()).isTrue();
  }

  @Test
  void leavesACurrentCursorRuleAloneAndRefreshesAStaleOne() {
    ProjectState current =
        project()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", cursorRule(carrier.coordinate()))
            .build();
    assertThat(on(plan(current), CURSOR_RULE)).isEmpty();

    ProjectState stale =
        project()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", cursorRule("a:b:0.0.1"))
            .build();
    assertThat(on(plan(stale), CURSOR_RULE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan(stale), CURSOR_RULE)).isEqualTo(cursorRule(carrier.coordinate()));
  }

  @Test
  void adoptsTheUnstampedRuleThePluginShipsWithoutAskingForForce() {
    ProjectState state =
        project()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", cursorRule(""))
            .build();

    InitPlan plan = plan(state);

    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(AdoptPage.class);
    assertThat(after(plan, CURSOR_RULE)).isEqualTo(cursorRule(carrier.coordinate()));
  }

  @Test
  void refusesARuleFileSomebodyElseWroteAtOurPath() {
    ProjectState state =
        project()
            .detectedPlatform("cursor")
            .pointerFile(".cursor/rules/narrativetrace.mdc", "---\nalwaysApply: true\n---\nmine\n")
            .build();

    Action action = on(plan(state), CURSOR_RULE).orElseThrow();

    assertThat(action).isInstanceOf(Refuse.class);
    assertThat(((Refuse) action).reason()).contains("not written by narrativetrace");
  }

  // --- links, scope and the mixed case
  // ------------------------------------------------------------

  @Test
  void refusesAPointerWhosePathRunsThroughASymbolicLink() {
    ProjectState state =
        project()
            .detectedPlatform("cursor")
            .detectedPlatform("replit")
            .pointerBehindLink(".cursor/rules/narrativetrace.mdc")
            .pointerBehindLink("replit.md")
            .build();

    InitPlan plan = plan(state);

    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(Refuse.class);
    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(Refuse.class);
    assertThat(((Refuse) on(plan, REPLIT_MD).orElseThrow()).reason()).contains("symbolic link");
  }

  @Test
  void plansBothPointersWhenTwoMarkersArePresentAtOnce() {
    ProjectState state = project().detectedPlatform("cursor").detectedPlatform("replit").build();

    InitPlan plan = plan(state);

    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(CreateFile.class);
    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(CreateFile.class);
  }

  @Test
  void aSkillsOnlyRunWritesNoPointer() {
    ProjectState state = project().detectedPlatform("cursor").detectedPlatform("replit").build();

    InitPlan plan =
        InitPlanner.plan(
            state, carrier, InitOptions.defaults().withScope(Scope.SKILLS), CommandRoute.CLI);

    assertThat(on(plan, REPLIT_MD)).isEmpty();
    assertThat(on(plan, CURSOR_RULE)).isEmpty();
  }
}
