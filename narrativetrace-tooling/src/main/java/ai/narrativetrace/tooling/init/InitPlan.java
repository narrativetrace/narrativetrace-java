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
import java.util.List;

/**
 * Everything an install or an uninstall would do, decided before anything is written.
 *
 * <p>INTENT: the seam that makes approval structural. A plan can be rendered as a diff, reviewed,
 * and only then applied — and because it is complete, applying it needs no second look at the
 * project.
 *
 * <p><b>@llmNote</b> A plan holds at most ONE action per path. Two actions on one path would make
 * the diff a lie (the second would be computed from a state the first has not produced), so the
 * planners never emit one and the invariant refuses it.
 *
 * @param carrier the coordinate everything in this plan is stamped with
 * @param dryRun whether this plan is for showing only — a dry run exits 0 even when it refuses
 * @param actions the actions, in the order a person should read them
 */
public record InitPlan(String carrier, boolean dryRun, List<Action> actions) {

  public InitPlan {
    if (carrier == null || carrier.isBlank()) {
      throw new IllegalArgumentException("a plan names the carrier it came from");
    }
    if (actions == null) {
      throw new IllegalArgumentException("a plan with no actions is an empty list, never null");
    }
    actions = List.copyOf(actions);
    assert invariantOf(actions) : "a plan holds at most one action per path";
  }

  /** The invariant, in a form the compact constructor can reach before the record exists. */
  private static boolean invariantOf(List<Action> actions) {
    List<Path> paths = actions.stream().map(Action::path).toList();
    return paths.stream().distinct().count() == paths.size();
  }

  /** True when there is nothing to do — the shape a re-run of an up-to-date install produces. */
  public boolean isEmpty() {
    return actions.isEmpty();
  }

  /** True when at least one action is a refusal. */
  public boolean hasRefusals() {
    return !refusals().isEmpty();
  }

  /** Every refusal, in plan order. */
  public List<Action.Refuse> refusals() {
    return actions.stream()
        .filter(Action.Refuse.class::isInstance)
        .map(Action.Refuse.class::cast)
        .toList();
  }

  /**
   * The exit code an entry point returns: 1 when anything was refused, 0 otherwise — and always 0
   * for a dry run, where a refusal is something shown rather than something that happened.
   */
  public int exitCode() {
    return !dryRun && hasRefusals() ? 1 : 0;
  }

  /** One action per path, every path project-relative, every refusal with a reason. */
  boolean invariant() {
    List<Path> paths = actions.stream().map(Action::path).toList();
    return paths.stream().distinct().count() == paths.size()
        && actions.stream().noneMatch(action -> action.path().isAbsolute());
  }
}
