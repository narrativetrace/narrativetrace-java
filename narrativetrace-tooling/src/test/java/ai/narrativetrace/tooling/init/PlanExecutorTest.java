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

import ai.narrativetrace.tooling.init.ExecutionReport.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The only class in the installer that writes. Everything here happens on a real temp directory.
 */
class PlanExecutorTest {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static InitPlan plan(Action... actions) {
    return new InitPlan(COORDINATE, false, List.of(actions));
  }

  private static String read(Path file) throws IOException {
    return Files.readString(file);
  }

  @Test
  void createsAFileAndEveryDirectoryAboveIt(@TempDir Path dir) throws IOException {
    Path page = Path.of(".agents/skills/doctor/SKILL.md");

    ExecutionReport report = PlanExecutor.execute(plan(new Action.CreateFile(page, "page\n")), dir);

    assertThat(read(dir.resolve(page))).isEqualTo("page\n");
    assertThat(report.exitCode()).isZero();
    assertThat(report.results())
        .singleElement()
        .extracting(ExecutionReport.Applied::status)
        .isEqualTo(Status.APPLIED);
  }

  @Test
  void writesExactlyTheBytesThePlanCarries(@TempDir Path dir) throws IOException {
    String windows = "﻿# Agents\r\n\r\nblock\r\n";

    PlanExecutor.execute(plan(new Action.CreateFile(Path.of("AGENTS.md"), windows)), dir);

    assertThat(Files.readString(dir.resolve("AGENTS.md"))).isEqualTo(windows);
  }

  @Test
  void replacesAnExistingFile(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("AGENTS.md"), "old\n");

    PlanExecutor.execute(
        plan(new Action.ReplaceBlock(Path.of("AGENTS.md"), "old\n", "new\n")), dir);

    assertThat(read(dir.resolve("AGENTS.md"))).isEqualTo("new\n");
  }

  @Test
  void appliesWhatTheActionComputedNotWhatIsOnDisk(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("AGENTS.md"), "# Agents\n");

    PlanExecutor.execute(
        plan(new Action.AppendBlock(Path.of("AGENTS.md"), "# Agents\n", "block\n")), dir);

    assertThat(read(dir.resolve("AGENTS.md"))).isEqualTo("# Agents\n\nblock\n");
  }

  @Test
  void deletesAFileAndThenItsDirectory(@TempDir Path dir) throws IOException {
    Path page = dir.resolve(".agents/skills/doctor/SKILL.md");
    Files.createDirectories(page.getParent());
    Files.writeString(page, "page\n");

    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.DeleteFile(Path.of(".agents/skills/doctor/SKILL.md"), "page\n"),
                new Action.DeleteDirectory(Path.of(".agents/skills/doctor"))),
            dir);

    assertThat(page).doesNotExist();
    assertThat(page.getParent()).doesNotExist();
    assertThat(report.exitCode()).isZero();
  }

  @Test
  void deletingSomethingAlreadyGoneIsStillApplied(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.DeleteFile(Path.of("AGENTS.md"), "gone\n"),
                new Action.DeleteDirectory(Path.of(".agents/skills/doctor"))),
            dir);

    assertThat(report.exitCode()).isZero();
    assertThat(report.results())
        .extracting(ExecutionReport.Applied::status)
        .containsOnly(Status.APPLIED);
  }

  @Test
  void leavesADirectoryThatStillHoldsSomebodyElsesFileAndSaysSo(@TempDir Path dir)
      throws IOException {
    Path directory = dir.resolve(".agents/skills/doctor");
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("NOTES.md"), "mine\n");

    ExecutionReport report =
        PlanExecutor.execute(
            plan(new Action.DeleteDirectory(Path.of(".agents/skills/doctor"))), dir);

    assertThat(directory).exists();
    assertThat(directory.resolve("NOTES.md")).exists();
    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(report.results().get(0).detail()).contains("not empty");
  }

  @Test
  void reportsARefusalAndWritesNothingForIt(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(plan(new Action.Refuse(Path.of("AGENTS.md"), "two sections")), dir);

    assertThat(dir.resolve("AGENTS.md")).doesNotExist();
    assertThat(report.results().get(0).status()).isEqualTo(Status.REFUSED);
    assertThat(report.results().get(0).detail()).isEqualTo("two sections");
    assertThat(report.exitCode()).isEqualTo(1);
  }

  @Test
  void aFailedWriteLeavesWhatWasThereIntactAndTheRestOfThePlanRuns(@TempDir Path dir)
      throws IOException {
    Files.createDirectories(dir.resolve("AGENTS.md"));
    Files.writeString(dir.resolve("AGENTS.md/inside.txt"), "untouched\n");

    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.ReplaceBlock(Path.of("AGENTS.md"), "old\n", "new\n"),
                new Action.CreateFile(Path.of("NOTES.md"), "written\n")),
            dir);

    assertThat(read(dir.resolve("AGENTS.md/inside.txt"))).isEqualTo("untouched\n");
    assertThat(read(dir.resolve("NOTES.md"))).isEqualTo("written\n");
    assertThat(report.results().get(0).status()).isEqualTo(Status.REFUSED);
    assertThat(report.results().get(1).status()).isEqualTo(Status.APPLIED);
    assertThat(report.exitCode()).isEqualTo(1);
  }

  @Test
  void leavesNoTemporaryFileBehind(@TempDir Path dir) throws IOException {
    PlanExecutor.execute(
        plan(
            new Action.CreateFile(Path.of("AGENTS.md"), "a\n"),
            new Action.CreateFile(Path.of("CLAUDE.md"), "b\n")),
        dir);

    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactlyInAnyOrder("AGENTS.md", "CLAUDE.md");
    }
  }

  @Test
  void reportsTheCarrierAndEveryActionInPlanOrder(@TempDir Path dir) {
    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.CreateFile(Path.of("AGENTS.md"), "a\n"),
                new Action.Refuse(Path.of("CLAUDE.md"), "no flag")),
            dir);

    assertThat(report.carrier()).isEqualTo(COORDINATE);
    assertThat(report.results())
        .extracting(applied -> applied.action().path().toString())
        .containsExactly("AGENTS.md", "CLAUDE.md");
    assertThat(report.hasRefusals()).isTrue();
  }

  // --- links ----------------------------------------------------------------------------------

  /**
   * Replacing a link is the one write that starts by deleting something: the link goes, a real
   * directory takes its place, and what the link pointed at is left exactly as it was — which is
   * the whole point, because after `npx skills add` it points at the page this install just
   * adopted.
   */
  @Test
  void replacesALinkWithARealDirectoryAndLeavesItsTargetAlone(@TempDir Path dir)
      throws IOException {
    Path reached = dir.resolve(".agents/skills/doctor/SKILL.md");
    Files.createDirectories(reached.getParent());
    Files.writeString(reached, "the agents page\n");
    Files.createDirectories(dir.resolve(".claude/skills"));
    Files.createSymbolicLink(
        dir.resolve(".claude/skills/doctor"), Path.of("../../.agents/skills/doctor"));

    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.ReplaceLink(
                    Path.of(".claude/skills/doctor"),
                    Path.of(".claude/skills/doctor/SKILL.md"),
                    "../../.agents/skills/doctor",
                    "the vendor page\n")),
            dir);

    assertThat(report.exitCode()).isZero();
    assertThat(Files.isSymbolicLink(dir.resolve(".claude/skills/doctor"))).isFalse();
    assertThat(read(dir.resolve(".claude/skills/doctor/SKILL.md"))).isEqualTo("the vendor page\n");
    assertThat(read(reached)).isEqualTo("the agents page\n");
  }

  @Test
  void replacesALinkedPageWithARealFile(@TempDir Path dir) throws IOException {
    Files.createDirectories(dir.resolve(".agents/skills/doctor"));
    Files.writeString(dir.resolve("elsewhere.md"), "somebody's page\n");
    Path page = Path.of(".agents/skills/doctor/SKILL.md");
    Files.createSymbolicLink(dir.resolve(page), Path.of("../../../elsewhere.md"));

    PlanExecutor.execute(
        plan(new Action.ReplaceLink(page, page, "../../../elsewhere.md", "ours\n")), dir);

    assertThat(Files.isSymbolicLink(dir.resolve(page))).isFalse();
    assertThat(read(dir.resolve(page))).isEqualTo("ours\n");
    assertThat(read(dir.resolve("elsewhere.md"))).isEqualTo("somebody's page\n");
  }

  /**
   * The guarantee for a link no planner saw — one created between the read and the write, or one
   * further up the path than the planners look. The write is refused, not followed, and the rest of
   * the plan still runs.
   */
  @Test
  void refusesToWriteThroughALinkNoPlanKnewAbout(@TempDir Path dir) throws IOException {
    Path outside = Files.createDirectories(dir.getParent().resolve("outside-" + dir.getFileName()));
    Files.createDirectories(dir.resolve(".claude"));
    Files.createSymbolicLink(dir.resolve(".claude/skills"), outside);

    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.CreateFile(Path.of(".claude/skills/doctor/SKILL.md"), "page\n"),
                new Action.CreateFile(Path.of("AGENTS.md"), "section\n")),
            dir);

    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(report.results().get(0).detail()).contains("symbolic link");
    assertThat(outside.resolve("doctor")).doesNotExist();
    assertThat(read(dir.resolve("AGENTS.md"))).isEqualTo("section\n");
  }

  /** An uninstall never deletes through a link either: the page at the other end is not ours. */
  @Test
  void refusesToDeleteThroughALink(@TempDir Path dir) throws IOException {
    Path reached = dir.resolve(".agents/skills/doctor/SKILL.md");
    Files.createDirectories(reached.getParent());
    Files.writeString(reached, "the agents page\n");
    Files.createDirectories(dir.resolve(".claude/skills"));
    Files.createSymbolicLink(
        dir.resolve(".claude/skills/doctor"), Path.of("../../.agents/skills/doctor"));

    ExecutionReport report =
        PlanExecutor.execute(
            plan(
                new Action.DeleteFile(
                    Path.of(".claude/skills/doctor/SKILL.md"), "the agents page\n")),
            dir);

    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(read(reached)).isEqualTo("the agents page\n");
  }

  @Test
  void anEmptyPlanAppliesCleanly(@TempDir Path dir) {
    ExecutionReport report = PlanExecutor.execute(plan(), dir);

    assertThat(report.results()).isEmpty();
    assertThat(report.exitCode()).isZero();
    assertThat(report.hasRefusals()).isFalse();
  }

  @Test
  void refusesToApplyADryRunPlan(@TempDir Path dir) {
    InitPlan dry = new InitPlan(COORDINATE, true, List.of());

    assertThatThrownBy(() -> PlanExecutor.execute(dry, dir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dry run");
  }

  @Test
  void refusesToApplyToSomethingThatIsNotADirectory(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("a-file");
    Files.writeString(file, "x");

    assertThatThrownBy(() -> PlanExecutor.execute(plan(), file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a directory");
    assertThatThrownBy(() -> PlanExecutor.execute(null, dir))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PlanExecutor.execute(plan(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
