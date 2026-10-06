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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a person and a script each see. The JSON envelope mirrors the doctor's, on purpose. */
class PlanRendererTest {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static InitPlan plan(Action... actions) {
    return new InitPlan(COORDINATE, false, List.of(actions));
  }

  // --- text ------------------------------------------------------------------------------------

  @Test
  void textNamesTheCarrierAndOneLinePerAction() {
    String text =
        PlanRenderer.renderText(
            plan(
                new Action.CreateFile(Path.of(".agents/skills/doctor/SKILL.md"), "page\n"),
                new Action.Refuse(Path.of("AGENTS.md"), "two sections")));

    assertThat(text)
        .contains(COORDINATE)
        .contains("create  .agents/skills/doctor/SKILL.md")
        .contains("refuse  AGENTS.md — two sections")
        .contains("2 action(s), 1 refusal(s)");
  }

  @Test
  void textSaysSoWhenThereIsNothingToDo() {
    assertThat(PlanRenderer.renderText(plan())).contains("nothing to do");
  }

  @Test
  void textOfAReportSaysWhatHappened(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.CreateFile(Path.of("AGENTS.md"), "a\n"),
                new Action.Refuse(Path.of("CLAUDE.md"), "no flag")),
            dir);

    String text = PlanRenderer.renderText(report);

    assertThat(text)
        .contains("applied create  AGENTS.md")
        .contains("refused refuse  CLAUDE.md — no flag")
        .contains("1 applied, 1 refused");
  }

  /**
   * An adoption says so on BOTH surfaces — the preview a person reads and the report of what
   * happened. Seeing "replace" where nothing of theirs was replaced is the misreading this avoids.
   */
  @Test
  void bothThePlanAndTheReportSayAPageWasAdopted(@TempDir Path dir) {
    Action.AdoptPage adoption =
        new Action.AdoptPage(Path.of(".agents/skills/doctor/SKILL.md"), "page\n", "stamped\n");

    String preview = PlanRenderer.renderText(plan(adoption));
    String applied = PlanRenderer.renderText(PlanExecutor.execute(plan(adoption), dir));

    assertThat(preview).contains("adopt   .agents/skills/doctor/SKILL.md").contains("adopted:");
    assertThat(applied)
        .contains("applied adopt   .agents/skills/doctor/SKILL.md")
        .contains("adopted:");
  }

  // --- JSON ------------------------------------------------------------------------------------

  @Test
  void jsonOfAPlanIsTheDoctorsEnvelopeWithPlannedActions() {
    String json =
        PlanRenderer.renderJson(
            plan(
                new Action.CreateFile(Path.of(".agents/skills/doctor/SKILL.md"), "page\n"),
                new Action.Refuse(Path.of("AGENTS.md"), "two sections")));

    assertThat(json)
        .isEqualTo(
            """
            {
              "carrier": "ai.narrativetrace:narrativetrace-skills:1.2.3",
              "actions": [
                {"kind": "create", "path": ".agents/skills/doctor/SKILL.md", "status": "planned"},
                {"kind": "refuse", "path": "AGENTS.md", "status": "refused"}
              ],
              "exitCode": 1
            }
            """);
  }

  @Test
  void jsonOfAnEmptyPlanStillCarriesTheEnvelope() {
    assertThat(PlanRenderer.renderJson(plan()))
        .isEqualTo(
            """
            {
              "carrier": "ai.narrativetrace:narrativetrace-skills:1.2.3",
              "actions": [
              ],
              "exitCode": 0
            }
            """);
  }

  @Test
  void jsonOfADryRunExitsZero() {
    InitPlan dry = new InitPlan(COORDINATE, true, List.of(new Action.Refuse(Path.of("A.md"), "x")));

    assertThat(PlanRenderer.renderJson(dry)).contains("\"exitCode\": 0");
  }

  @Test
  void jsonOfAReportCarriesWhatHappened(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(plan(new Action.CreateFile(Path.of("AGENTS.md"), "a\n")), dir);

    assertThat(PlanRenderer.renderJson(report))
        .contains("\"status\": \"applied\"")
        .contains("\"exitCode\": 0");
  }

  @Test
  void jsonOfAReportIsTheSameEnvelopeRowForRow(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.CreateFile(Path.of("AGENTS.md"), "a\n"),
                new Action.Refuse(Path.of("CLAUDE.md"), "no flag")),
            dir);

    assertThat(PlanRenderer.renderJson(report))
        .isEqualTo(
            """
            {
              "carrier": "ai.narrativetrace:narrativetrace-skills:1.2.3",
              "actions": [
                {"kind": "create", "path": "AGENTS.md", "status": "applied"},
                {"kind": "refuse", "path": "CLAUDE.md", "status": "refused"}
              ],
              "exitCode": 1
            }
            """);
  }

  @Test
  void jsonEscapesAControlCharacterAsAUnicodeEscape() {
    String json = PlanRenderer.renderJson(plan(new Action.CreateFile(Path.of("a\u0001b.md"), "x")));

    assertThat(json).contains("\"path\": \"a\\u0001b.md\"");
  }

  /** A space is 0x20, the first character that must NOT become a \\u escape. */
  @Test
  void jsonLeavesASpaceInAPathAlone() {
    String json = PlanRenderer.renderJson(plan(new Action.CreateFile(Path.of("my notes.md"), "x")));

    assertThat(json).contains("\"path\": \"my notes.md\"");
  }

  @Test
  void jsonEscapesWhatEverEndsUpInAPath() {
    String json =
        PlanRenderer.renderJson(
            plan(new Action.CreateFile(Path.of(".agents/sk\"ill\\x/SKILL.md"), "p")));

    assertThat(json).contains("\"path\": \".agents/sk\\\"ill\\\\x/SKILL.md\"");
  }

  // --- diff ------------------------------------------------------------------------------------

  @Test
  void diffOfACreatedFileComesFromNowhere() {
    String diff =
        PlanRenderer.renderDiff(plan(new Action.CreateFile(Path.of("AGENTS.md"), "a\nb\n")));

    assertThat(diff)
        .isEqualTo(
            """
            --- /dev/null
            +++ b/AGENTS.md
            @@ -0,0 +1,2 @@
            +a
            +b
            """);
  }

  @Test
  void diffOfADeletedFileGoesNowhere() {
    String diff = PlanRenderer.renderDiff(plan(new Action.DeleteFile(Path.of("AGENTS.md"), "a\n")));

    assertThat(diff)
        .isEqualTo(
            """
            --- a/AGENTS.md
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -a
            """);
  }

  @Test
  void diffOfAReplacementShowsOnlyWhatChangedWithContext() {
    String before = "1\n2\n3\n4\n5\nold\n6\n7\n8\n9\n";
    String after = "1\n2\n3\n4\n5\nnew\n6\n7\n8\n9\n";

    String diff =
        PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), before, after)));

    assertThat(diff)
        .isEqualTo(
            """
            --- a/A.md
            +++ b/A.md
            @@ -3,7 +3,7 @@
             3
             4
             5
            -old
            +new
             6
             7
             8
            """);
  }

  @Test
  void diffMarksAMissingFinalNewlineOnBothSides() {
    String diff = PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), "a", "b")));

    assertThat(diff)
        .isEqualTo(
            """
            --- a/A.md
            +++ b/A.md
            @@ -1,1 +1,1 @@
            -a
            \\ No newline at end of file
            +b
            \\ No newline at end of file
            """);
  }

  @Test
  void diffShowsALineEndingChangeRatherThanNothing() {
    String diff =
        PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), "a\n", "a\r\n")));

    assertThat(diff).contains("-a").contains("+a\r");
  }

  @Test
  void diffDescribesTheActionsThatHaveNoContent() {
    String diff =
        PlanRenderer.renderDiff(
            plan(
                new Action.DeleteDirectory(Path.of(".agents/skills/doctor")),
                new Action.Refuse(Path.of("AGENTS.md"), "two sections")));

    assertThat(diff)
        .isEqualTo(
            """
            # .agents/skills/doctor — delete-directory
            # AGENTS.md — refused: two sections
            """);
  }

  /**
   * An inserted line identical to its neighbour: the suffix scan must stop where the prefix did.
   */
  @Test
  void diffOfALineInsertedBesideItsTwinCountsEachSideOnce() {
    String diff =
        PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), "a\n", "a\na\n")));

    assertThat(diff)
        .isEqualTo(
            """
            --- a/A.md
            +++ b/A.md
            @@ -1,1 +1,2 @@
             a
            +a
            """);
  }

  /** The mirror of the case above: a REMOVED line identical to its neighbour. */
  @Test
  void diffOfALineRemovedBesideItsTwinCountsEachSideOnce() {
    String diff =
        PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), "a\na\n", "a\n")));

    assertThat(diff)
        .isEqualTo(
            """
            --- a/A.md
            +++ b/A.md
            @@ -1,2 +1,1 @@
             a
            -a
            """);
  }

  @Test
  void diffOfAnUnchangedFileIsEmpty() {
    assertThat(
            PlanRenderer.renderDiff(plan(new Action.ReplaceBlock(Path.of("A.md"), "a\n", "a\n"))))
        .isEmpty();
  }

  @Test
  void refusesToRenderNothing() {
    assertThatThrownBy(() -> PlanRenderer.renderText((InitPlan) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PlanRenderer.renderJson((InitPlan) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PlanRenderer.renderDiff(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PlanRenderer.renderText((ExecutionReport) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PlanRenderer.renderJson((ExecutionReport) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- the one decision every entry point shares -------------------------------------------

  @Test
  void aPlanRendersAsTheSummaryAndTheDiffTogether() {
    InitPlan plan = new InitPlan(COORDINATE, true, List.of(create()));

    assertThat(PlanRenderer.render(plan, false))
        .isEqualTo(PlanRenderer.renderText(plan) + PlanRenderer.renderDiff(plan));
  }

  @Test
  void aPlanRendersAsNothingButTheEnvelopeWhenJsonIsAsked() {
    InitPlan plan = new InitPlan(COORDINATE, true, List.of(create()));

    assertThat(PlanRenderer.render(plan, true)).isEqualTo(PlanRenderer.renderJson(plan));
  }

  @Test
  void anAppliedRunRendersAsItsOwnSummaryOrItsOwnEnvelope() {
    ExecutionReport report =
        new ExecutionReport(
            COORDINATE,
            List.of(new ExecutionReport.Applied(create(), ExecutionReport.Status.APPLIED, "")));

    assertThat(PlanRenderer.render(report, false)).isEqualTo(PlanRenderer.renderText(report));
    assertThat(PlanRenderer.render(report, true)).isEqualTo(PlanRenderer.renderJson(report));
  }

  private static Action create() {
    return new Action.CreateFile(java.nio.file.Path.of("AGENTS.md"), "# Agents\n");
  }
}
