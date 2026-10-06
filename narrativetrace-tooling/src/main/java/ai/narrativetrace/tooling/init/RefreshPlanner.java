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

import java.util.List;
import java.util.stream.Stream;

/**
 * Decides what a build-time refresh would rewrite: the pages and the managed section a previous
 * install left behind, brought up to the carrier the build now resolves.
 *
 * <p>INTENT: a build may KEEP an install current; it may never start one. That is the whole of the
 * difference from {@link InitPlanner}, and it is why this planner exists rather than a flag —
 * "never create" has to be true by construction, not by a caller remembering an option.
 *
 * <p><b>@sideEffects</b> None. Pure, like the other two planners.
 */
public final class RefreshPlanner {

  private RefreshPlanner() {}

  /** The plan a refresh against this carrier would apply — empty unless something is stale. */
  public static InitPlan plan(ProjectState state, Carrier carrier) {
    if (state == null || carrier == null) {
      throw new IllegalArgumentException("planning a refresh needs a project state and a carrier");
    }
    return new InitPlan(carrier.coordinate(), false, rewrites(state, carrier));
  }

  /**
   * The install plan, kept down to the actions that REWRITE something already ours. Every other
   * kind — a page to create, a section to append, an import line to add, a refusal — is dropped
   * here, which is what keeps a build from starting an install nobody asked for.
   */
  private static List<Action> rewrites(ProjectState state, Carrier carrier) {
    if (!isStale(state, carrier.coordinate())) {
      return List.of();
    }
    return InitPlanner.plan(state, carrier, InitOptions.defaults()).actions().stream()
        .filter(Action.ReplaceBlock.class::isInstance)
        .toList();
  }

  /**
   * Whether this project carries an install of ours at all — one skill directory whose page has our
   * provenance line.
   *
   * <p><b>@llmNote</b> This is the question a build asks BEFORE it reaches for a carrier. A project
   * that never ran {@code init} must never resolve one, or every offline build of every project
   * would warn about skills nobody installed. A directory somebody else owns is not an install of
   * ours, however it is named.
   */
  public static boolean isInstalled(ProjectState state) {
    if (state == null) {
      throw new IllegalArgumentException("asking what a project carries needs a project state");
    }
    return ours(state).findAny().isPresent();
  }

  /**
   * Whether this project carries an install of ours from a DIFFERENT carrier. A project with no
   * install of ours is never stale: a build refreshes what init put there and starts nothing.
   */
  private static boolean isStale(ProjectState state, String coordinate) {
    return ours(state).anyMatch(skill -> !skill.coordinate().equals(coordinate));
  }

  private static Stream<InstalledSkill> ours(ProjectState state) {
    return state.installedSkills().stream()
        .filter(skill -> skill.presence() == InstalledSkill.Presence.OURS);
  }
}
