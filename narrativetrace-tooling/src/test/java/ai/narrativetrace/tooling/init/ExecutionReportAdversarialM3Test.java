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
import org.junit.jupiter.api.Test;

/**
 * The contract {@code Action.Refuse} already holds — a refusal carries a reason — held on the other
 * side too, where a refusal is recorded rather than planned.
 *
 * <p>Found by the adversarial pass: a hand-built refusal with no detail rendered as {@code path ()}
 * in the refresh task's warning, an empty parenthesis nobody could act on. The answer is not to
 * special-case the rendering; it is that a refusal nobody can explain must not exist.
 */
class ExecutionReportAdversarialM3Test {

  private static final Action ACTION = new Action.CreateFile(Path.of("AGENTS.md"), "# Agents\n");

  @Test
  void refusesToRecordARefusalThatCarriesNoReason() {
    assertThatThrownBy(
            () -> new ExecutionReport.Applied(ACTION, ExecutionReport.Status.REFUSED, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    assertThatThrownBy(
            () -> new ExecutionReport.Applied(ACTION, ExecutionReport.Status.REFUSED, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** An APPLIED result has nothing to explain, so its empty detail stays legal. */
  @Test
  void anAppliedResultNeedsNoDetail() {
    var applied = new ExecutionReport.Applied(ACTION, ExecutionReport.Status.APPLIED, "");

    assertThat(applied.detail()).isEmpty();
  }
}
