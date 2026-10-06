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

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a plan to a project directory. The only class in the installer that writes.
 *
 * <p>INTENT: keeps the decisions and the writing apart. The executor asks the project NOTHING — a
 * plan carries every file's whole text — so what a person reviewed as a diff is exactly what lands,
 * and the planner stays testable without a filesystem.
 *
 * <p><b>@llmNote</b> Every write is temp-file-then-atomic-move inside the TARGET directory, so a
 * failed write leaves the original file exactly as it was; a rename across filesystems, which is
 * what a temp directory elsewhere would force, is not atomic.
 *
 * <p><b>@llmNote</b> Nothing here throws on a file that will not cooperate: the action is reported
 * as refused, the rest of the plan runs, and the exit code carries the news. A dry-run plan is the
 * one exception — applying one is a programming error, not a filesystem one.
 *
 * <p><b>@sideEffects</b> Creates, replaces and deletes files under the given directory, and creates
 * the directories above them.
 */
public final class PlanExecutor {

  private static final String TEMPORARY_PREFIX = ".narrativetrace-";

  private PlanExecutor() {}

  /**
   * Applies every action, in order.
   *
   * @throws IllegalArgumentException when the plan is a dry run, or the directory is not one
   */
  public static ExecutionReport execute(InitPlan plan, Path projectDirectory) {
    if (plan == null || projectDirectory == null) {
      throw new IllegalArgumentException("applying a plan needs the plan and a project directory");
    }
    if (plan.dryRun()) {
      throw new IllegalArgumentException("a dry run is shown, never applied");
    }
    if (!Files.isDirectory(projectDirectory)) {
      throw new IllegalArgumentException(projectDirectory + " is not a directory");
    }
    List<ExecutionReport.Applied> results = new ArrayList<>();
    for (Action action : plan.actions()) {
      results.add(apply(action, projectDirectory));
    }
    return new ExecutionReport(plan.carrier(), results);
  }

  /**
   * One action. Java 17 has no pattern switch, so the sealed hierarchy is walked with {@code
   * instanceof} — the order is exhaustive over {@link Action}'s permitted types.
   */
  private static ExecutionReport.Applied apply(Action action, Path projectDirectory) {
    Path target = projectDirectory.resolve(action.path());
    try {
      if (action instanceof Action.Refuse refusal) {
        return ExecutionReport.Applied.refused(action, refusal.reason());
      }
      if (action instanceof Action.ReplaceLink replacement) {
        return replaceLink(replacement, projectDirectory);
      }
      requireNoLinkOnTheWay(projectDirectory, action.path());
      if (action instanceof Action.DeleteDirectory) {
        return deleteDirectory(action, target);
      }
      return applyEdit(action, target, (Action.FileEdit) action);
    } catch (IOException e) {
      return ExecutionReport.Applied.refused(
          action, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static ExecutionReport.Applied applyEdit(Action action, Path target, Action.FileEdit edit)
      throws IOException {
    if (edit instanceof Action.DeleteFile) {
      Files.deleteIfExists(target);
    } else {
      write(target, edit.after());
    }
    return ExecutionReport.Applied.applied(action);
  }

  /** Removes a directory that is now empty; one that is not is reported, never emptied. */
  private static ExecutionReport.Applied deleteDirectory(Action action, Path target)
      throws IOException {
    try {
      Files.deleteIfExists(target);
      return ExecutionReport.Applied.applied(action);
    } catch (DirectoryNotEmptyException e) {
      return ExecutionReport.Applied.refused(
          action, target + " is not empty — something else is in it, so it was left alone");
    }
  }

  /**
   * The one action that begins by deleting: the link goes first — the link itself, never what it
   * points at — so the write that follows creates a real directory or file of the project's own.
   */
  private static ExecutionReport.Applied replaceLink(
      Action.ReplaceLink action, Path projectDirectory) throws IOException {
    Files.deleteIfExists(projectDirectory.resolve(action.link()));
    requireNoLinkOnTheWay(projectDirectory, action.path());
    write(projectDirectory.resolve(action.path()), action.after());
    return ExecutionReport.Applied.applied(action);
  }

  /**
   * No write and no delete ever passes THROUGH a symbolic link. The planners refuse every link they
   * can see; this is the guarantee for one they cannot — a link made between the read and the
   * write, or one further up the path than a planner looks. The action is refused like any other
   * filesystem refusal, and the rest of the plan still runs.
   */
  private static void requireNoLinkOnTheWay(Path projectDirectory, Path relative)
      throws IOException {
    Path walked = projectDirectory;
    for (Path element : relative) {
      walked = walked.resolve(element);
      if (Files.isSymbolicLink(walked)) {
        throw new IOException(walked + " is a symbolic link, and nothing is written through one");
      }
    }
  }

  /** Temp file beside the target, then an atomic move over it. */
  private static void write(Path target, String content) throws IOException {
    Path directory = target.getParent();
    Files.createDirectories(directory);
    Path temporary = Files.createTempFile(directory, TEMPORARY_PREFIX, ".tmp");
    try {
      Files.writeString(temporary, content);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
