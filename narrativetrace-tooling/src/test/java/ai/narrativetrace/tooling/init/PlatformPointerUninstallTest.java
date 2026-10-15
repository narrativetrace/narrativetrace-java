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

import ai.narrativetrace.tooling.init.Action.DeleteFile;
import ai.narrativetrace.tooling.init.Action.FileEdit;
import ai.narrativetrace.tooling.init.Action.Refuse;
import ai.narrativetrace.tooling.init.Action.ReplaceBlock;
import ai.narrativetrace.tooling.init.InitOptions.Scope;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** An uninstall removes exactly the pointers the installer wrote, and no more. */
class PlatformPointerUninstallTest {

  private static final Path REPLIT_MD = Path.of("replit.md");
  private static final Path CURSOR_RULE = Path.of(".cursor/rules/narrativetrace.mdc");
  private static final String RULE_TEXT = "Read the skills.";
  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";
  private static final String SECTION =
      "<!-- narrativetrace:start " + COORDINATE + " -->\nsection\n<!-- narrativetrace:end -->\n";

  private static InitPlan plan(ProjectState state) {
    return UninstallPlanner.plan(state, InitOptions.defaults());
  }

  private static Optional<Action> on(InitPlan plan, Path path) {
    return plan.actions().stream().filter(action -> action.path().equals(path)).findFirst();
  }

  private static String after(InitPlan plan, Path path) {
    return ((FileEdit) on(plan, path).orElseThrow()).after();
  }

  @Test
  void deletesACursorRuleThatIsNothingButOurSection() {
    ProjectState state =
        ProjectState.builder()
            .pointerFile(CURSOR_RULE.toString(), CursorRule.render(RULE_TEXT, COORDINATE))
            .build();

    assertThat(on(plan(state), CURSOR_RULE)).get().isInstanceOf(DeleteFile.class);
  }

  @Test
  void keepsAnythingElseInACursorRuleTheUserEdited() {
    String edited = CursorRule.render(RULE_TEXT, COORDINATE) + "\nMy own rule below.\n";
    ProjectState state = ProjectState.builder().pointerFile(CURSOR_RULE.toString(), edited).build();

    InitPlan plan = plan(state);

    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, CURSOR_RULE))
        .contains("My own rule below.")
        .doesNotContain("narrativetrace:");
  }

  @Test
  void leavesACursorRuleWithNoSectionOfOursUntouched() {
    ProjectState state =
        ProjectState.builder()
            .pointerFile(CURSOR_RULE.toString(), "---\nalwaysApply: true\n---\nmine\n")
            .build();

    assertThat(on(plan(state), CURSOR_RULE)).isEmpty();
  }

  @Test
  void refusesARuleWithAnUnclosedSectionRatherThanGuessing() {
    ProjectState state =
        ProjectState.builder()
            .pointerFile(
                CURSOR_RULE.toString(), "---\n---\n<!-- narrativetrace:start x -->\nnever closed\n")
            .build();

    assertThat(on(plan(state), CURSOR_RULE)).get().isInstanceOf(Refuse.class);
  }

  @Test
  void deletesAReplitMdTheInstallerCreatedAndKeepsOneItAppendedTo() {
    ProjectState created =
        ProjectState.builder()
            .pointerFile("replit.md", MarkedBlock.CREATED_NOTE + "\n" + SECTION)
            .build();
    assertThat(on(plan(created), REPLIT_MD)).get().isInstanceOf(DeleteFile.class);

    ProjectState appended =
        ProjectState.builder().pointerFile("replit.md", "# Notes\n\n" + SECTION).build();
    InitPlan plan = plan(appended);
    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(ReplaceBlock.class);
    assertThat(after(plan, REPLIT_MD)).isEqualTo("# Notes\n");
  }

  @Test
  void removesBothPointersWhenBothArePresent() {
    ProjectState state =
        ProjectState.builder()
            .pointerFile("replit.md", MarkedBlock.CREATED_NOTE + "\n" + SECTION)
            .pointerFile(CURSOR_RULE.toString(), CursorRule.render(RULE_TEXT, COORDINATE))
            .build();

    InitPlan plan = plan(state);

    assertThat(on(plan, REPLIT_MD)).get().isInstanceOf(DeleteFile.class);
    assertThat(on(plan, CURSOR_RULE)).get().isInstanceOf(DeleteFile.class);
  }

  @Test
  void aSkillsOnlyUninstallLeavesThePointersAlone() {
    ProjectState state =
        ProjectState.builder()
            .pointerFile(CURSOR_RULE.toString(), CursorRule.render(RULE_TEXT, COORDINATE))
            .build();

    InitPlan plan = UninstallPlanner.plan(state, InitOptions.defaults().withScope(Scope.SKILLS));

    assertThat(on(plan, CURSOR_RULE)).isEmpty();
  }
}
