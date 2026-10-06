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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The properties an installer has to hold for files nobody wrote by hand.
 *
 * <p>Three of them: planning after applying finds nothing left to do, installing and then
 * uninstalling leaves the project as it was, and a plan never touches one path twice. Each runs
 * against a real temp directory, because "apply" is only meaningful against a filesystem.
 */
class InstallerPropertyTest {

  private static final InitOptions PERMISSIVE =
      InitOptions.defaults().withWriteExisting(true).withForce(true);

  // --- the properties ---------------------------------------------------------------------------

  /**
   * Planning, applying and planning again leaves no work. A refusal may repeat — it is a decision
   * about a file, not a change to one — so what must be empty is the set of EDITS.
   */
  @Property(tries = 200)
  void planningAfterApplyingFindsNothingLeftToDo(
      @ForAll("contextFile") String agentsMd, @ForAll("contextFile") String claudeMd) {
    inATemporaryProject(
        project -> {
          write(project.resolve("AGENTS.md"), agentsMd);
          write(project.resolve("CLAUDE.md"), claudeMd);

          apply(project, InitPlanner.plan(read(project), Carriers.shared(), PERMISSIVE));
          InitPlan second = InitPlanner.plan(read(project), Carriers.shared(), PERMISSIVE);

          assertThat(edits(second))
              .as("a second install of the same carrier has nothing to write")
              .isEmpty();
        });
  }

  /**
   * Installing and then uninstalling gives the project back. The one documented difference: a file
   * the installer appended to is left ending with a newline, because a block has to start on its
   * own line and nothing records that the file lacked one.
   */
  @Property(tries = 200)
  void installingThenUninstallingLeavesTheProjectAsItWas(
      @ForAll("untouchedContextFile") String agentsMd,
      @ForAll("untouchedContextFile") String claudeMd) {
    inATemporaryProject(
        project -> {
          write(project.resolve("AGENTS.md"), agentsMd);
          write(project.resolve("CLAUDE.md"), claudeMd);
          Map<String, String> before = Projects.snapshotOf(project);

          apply(project, InitPlanner.plan(read(project), Carriers.shared(), PERMISSIVE));
          apply(project, UninstallPlanner.plan(read(project), PERMISSIVE));

          Map<String, String> after = Projects.snapshotOf(project);
          assertThat(after.keySet()).as("no file gained or lost").isEqualTo(before.keySet());
          after.forEach(
              (path, content) ->
                  assertThat(content)
                      .as("%s is as it was, but for a final newline an append had to add", path)
                      .isIn(before.get(path), endingWithNewline(before.get(path))));
        });
  }

  @Property(tries = 200)
  void aPlanNeverTouchesOnePathTwice(
      @ForAll("contextFile") String agentsMd, @ForAll("contextFile") String claudeMd) {
    inATemporaryProject(
        project -> {
          write(project.resolve("AGENTS.md"), agentsMd);
          write(project.resolve("CLAUDE.md"), claudeMd);
          Files.createDirectories(project.resolve(".claude"));

          InitPlan install = InitPlanner.plan(read(project), Carriers.shared(), PERMISSIVE);
          apply(project, install);
          InitPlan uninstall = UninstallPlanner.plan(read(project), PERMISSIVE);

          assertThat(paths(install)).doesNotHaveDuplicates();
          assertThat(paths(uninstall)).doesNotHaveDuplicates();
        });
  }

  /** What the plan promised is what the files say afterwards, byte for byte. */
  @Property(tries = 100)
  void whatWasPlannedIsWhatTheFilesSay(@ForAll("contextFile") String agentsMd) {
    inATemporaryProject(
        project -> {
          write(project.resolve("AGENTS.md"), agentsMd);

          InitPlan plan = InitPlanner.plan(read(project), Carriers.shared(), PERMISSIVE);
          apply(project, plan);

          for (Action.FileEdit edit : edits(plan)) {
            Path file = project.resolve(edit.path());
            assertThat(Files.exists(file) ? Files.readString(file) : "").isEqualTo(edit.after());
          }
        });
  }

  // --- generators ------------------------------------------------------------------------------

  /**
   * Context files as they come: markers, fences, import lines, byte-order marks, no final newline.
   */
  @Provide
  Arbitrary<String> contextFile() {
    return Arbitraries.of(
            "# Title",
            "",
            "some prose about the project",
            "```",
            "@AGENTS.md",
            "@AGENTS.md   ",
            "  <!-- narrativetrace:start -->",
            "<!-- narrativetrace:start ai.narrativetrace:narrativetrace-skills:0.0.1 -->",
            "<!-- narrativetrace:end -->",
            "<!-- narrativetrace:created -->",
            "<!-- narrativetrace:skills:start -->")
        .list()
        .ofMaxSize(10)
        .flatMap(InstallerPropertyTest::assemble);
  }

  /** The same, minus anything of ours: the shape the round trip is defined for. */
  @Provide
  Arbitrary<String> untouchedContextFile() {
    return Arbitraries.of(
            "# Title",
            "",
            "some prose about the project",
            "```",
            "  indented",
            "<!-- an unrelated comment -->",
            "@SOMETHING.md",
            "tail")
        .list()
        .ofMaxSize(10)
        .flatMap(InstallerPropertyTest::assemble);
  }

  /**
   * Joins lines with one of the two line endings, and sometimes drops the final newline or adds a
   * BOM.
   */
  private static Arbitrary<String> assemble(List<String> lines) {
    return Arbitraries.of("\n", "\r\n")
        .flatMap(
            eol ->
                Arbitraries.of(true, false)
                    .flatMap(
                        finalNewline ->
                            Arbitraries.of(true, false)
                                .map(bom -> text(lines, eol, finalNewline, bom))));
  }

  private static String text(List<String> lines, String eol, boolean finalNewline, boolean bom) {
    if (lines.isEmpty()) {
      return "";
    }
    String joined = String.join(eol, lines) + (finalNewline ? eol : "");
    return bom ? "﻿" + joined : joined;
  }

  // --- the project under test --------------------------------------------------------------------

  private static void inATemporaryProject(Projects.Case body) {
    Projects.inATemporaryOne("narrativetrace-install", body);
  }

  private static ProjectState read(Path project) {
    return ProjectStateReader.read(project);
  }

  private static void apply(Path project, InitPlan plan) {
    PlanExecutor.execute(plan, project);
  }

  private static List<Action.FileEdit> edits(InitPlan plan) {
    return plan.actions().stream()
        .filter(Action.FileEdit.class::isInstance)
        .map(Action.FileEdit.class::cast)
        .toList();
  }

  private static List<Path> paths(InitPlan plan) {
    return plan.actions().stream().map(Action::path).toList();
  }

  /**
   * The one documented exception to the round trip: a file the installer APPENDED to is left ending
   * with a newline, because a block has to start on its own line and nothing records that the file
   * lacked one. A file that was refused, or never touched, keeps its exact bytes — which is why
   * this is offered as an alternative rather than applied to every file.
   */
  private static String endingWithNewline(String content) {
    return content.isEmpty() || content.endsWith("\n") || content.endsWith("\r")
        ? content
        : content + MarkedBlock.eolOf(content);
  }

  private static void write(Path file, String content) throws IOException {
    Files.writeString(file, content);
  }
}
