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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides what an uninstall would remove — exactly what the installer wrote, and nothing beside it.
 *
 * <p>INTENT: the other half of the promise that makes an install safe to try. A skill directory is
 * removed only when its page carries our provenance line, whatever carrier stamped it; the managed
 * section is removed from its markers out; the import line is removed only when it is still
 * character for character the line the installer added.
 *
 * <p><b>@llmNote</b> A file is DELETED only when the installer created it (the {@code
 * narrativetrace:created} note on its first line) and nothing but our own content is left in it. A
 * file the installer merely appended to is always kept, even if removing our section empties it.
 *
 * <p><b>@sideEffects</b> None. Pure, like {@link InitPlanner}.
 */
public final class UninstallPlanner {

  /** What an uninstall reports as its carrier when the project carries no stamp at all. */
  static final String UNKNOWN_CARRIER = "ai.narrativetrace:narrativetrace-skills:unknown";

  private UninstallPlanner() {}

  /** The plan an uninstall with these options would apply to this project. */
  public static InitPlan plan(ProjectState state, InitOptions options) {
    if (state == null || options == null) {
      throw new IllegalArgumentException("planning an uninstall needs a project state and options");
    }
    List<Action> actions = new ArrayList<>();
    if (options.scope().includesSkills()) {
      planSkills(state, actions);
    }
    if (options.scope().includesAgentsMd()) {
      planAgentsMd(state, actions);
      planClaudeMd(state, actions);
      planRuleFiles(state, actions);
    }
    return new InitPlan(installedCoordinate(state), options.dryRun(), actions);
  }

  /** The page first, then the directory it was the only reason for. */
  private static void planSkills(ProjectState state, List<Action> actions) {
    for (InstalledSkill skill : state.installedSkills()) {
      if (skill.presence() == InstalledSkill.Presence.OURS) {
        actions.add(new Action.DeleteFile(skill.page(), skill.body()));
        actions.add(new Action.DeleteDirectory(skill.directory()));
      }
    }
  }

  private static void planAgentsMd(ProjectState state, List<Action> actions) {
    state.agentsMd().ifPresent(text -> removeSection(InitPlanner.AGENTS_MD, text, true, actions));
  }

  /** A rule file is never deleted: the installer never created one. */
  private static void planRuleFiles(ProjectState state, List<Action> actions) {
    state
        .markedRuleFiles()
        .forEach((path, text) -> removeSection(Path.of(path), text, false, actions));
  }

  private static void removeSection(
      Path path, String text, boolean mayDelete, List<Action> actions) {
    MarkedBlock.Scan scan = MarkedBlock.scan(text);
    if (!scan.problems().isEmpty() || scan.regions().size() > 1) {
      actions.add(
          new Action.Refuse(
              path,
              path + " does not carry exactly one NarrativeTrace section — remove it by hand"));
      return;
    }
    if (scan.regions().isEmpty()) {
      return;
    }
    String withoutSection = MarkedBlock.remove(text, scan.regions().get(0));
    Optional<MarkedBlock.Line> created =
        MarkedBlock.lineIs(withoutSection, MarkedBlock.CREATED_NOTE);
    String remainder =
        created.map(line -> MarkedBlock.remove(withoutSection, line)).orElse(withoutSection);
    if (mayDelete && created.isPresent() && remainder.isBlank()) {
      actions.add(new Action.DeleteFile(path, text));
      return;
    }
    actions.add(new Action.ReplaceBlock(path, text, remainder));
  }

  private static void planClaudeMd(ProjectState state, List<Action> actions) {
    state
        .claudeMd()
        .ifPresent(
            text ->
                MarkedBlock.lineIs(text, InitPlanner.IMPORT_LINE)
                    .ifPresent(
                        line ->
                            actions.add(
                                new Action.ReplaceBlock(
                                    InitPlanner.CLAUDE_MD, text, MarkedBlock.remove(text, line)))));
  }

  /**
   * The coordinate this project was installed from: the stamp on the managed section, else the one
   * on the first skill of ours, else an honest {@code unknown}.
   */
  private static String installedCoordinate(ProjectState state) {
    Optional<String> fromSection =
        state
            .agentsMd()
            .map(MarkedBlock::scan)
            .filter(MarkedBlock.Scan::hasExactlyOneRegion)
            .map(scan -> scan.regions().get(0).coordinate())
            .filter(coordinate -> !coordinate.isEmpty());
    return fromSection
        .or(
            () ->
                state.installedSkills().stream()
                    .filter(skill -> skill.presence() == InstalledSkill.Presence.OURS)
                    .map(InstalledSkill::coordinate)
                    .findFirst())
        .orElse(UNKNOWN_CARRIER);
  }
}
