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

/**
 * What actually happened when a plan was applied, action by action.
 *
 * <p>INTENT: the answer a caller reports and exits on. A refusal — planned, or discovered when the
 * filesystem would not cooperate — is DATA here, never an exception, so one impossible action never
 * costs a run the actions that did work.
 *
 * @param carrier the coordinate the plan was stamped with
 * @param results one entry per action, in plan order
 */
public record ExecutionReport(String carrier, List<Applied> results) {

  /** Whether an action happened. */
  public enum Status {
    /** The filesystem now says what the action promised. */
    APPLIED,
    /** Nothing was done, and {@link Applied#detail()} says why. */
    REFUSED
  }

  /**
   * One action's outcome.
   *
   * <p><b>@llmNote</b> A {@link Status#REFUSED} result must carry a reason — the same contract
   * {@link Action.Refuse} holds on the planning side. A refusal nobody can act on is worse than no
   * refusal at all: it reaches a person as an empty parenthesis in a warning.
   *
   * @param action the action as planned
   * @param status whether it happened
   * @param detail why it did not, or {@code ""} when it did
   */
  public record Applied(Action action, Status status, String detail) {

    public Applied {
      if (action == null || status == null || detail == null) {
        throw new IllegalArgumentException(
            "an execution result needs an action, a status, a detail");
      }
      if (status == Status.REFUSED && detail.isBlank()) {
        throw new IllegalArgumentException(
            "a refusal carries the reason it refused — " + action.path() + " gives none");
      }
    }

    static Applied applied(Action action) {
      return new Applied(action, Status.APPLIED, "");
    }

    static Applied refused(Action action, String detail) {
      return new Applied(action, Status.REFUSED, detail);
    }
  }

  public ExecutionReport {
    if (carrier == null || carrier.isBlank()) {
      throw new IllegalArgumentException("a report names the carrier the plan came from");
    }
    results = List.copyOf(results);
  }

  /** True when anything was refused. */
  public boolean hasRefusals() {
    return results.stream().anyMatch(result -> result.status() == Status.REFUSED);
  }

  /** 1 when anything was refused, 0 otherwise — what an entry point returns. */
  public int exitCode() {
    return hasRefusals() ? 1 : 0;
  }
}
