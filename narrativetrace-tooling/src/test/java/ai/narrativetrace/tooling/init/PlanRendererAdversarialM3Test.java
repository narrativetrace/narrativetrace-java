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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boundary coverage for the two {@code render(..., boolean)} entry points: an EMPTY plan and a
 * report that is ALL refusals — the shapes {@code PlanRendererTest} exercises for the individual
 * {@code renderText}/{@code renderJson} methods but not through the one-decision {@code render}
 * overload every real caller actually uses.
 */
class PlanRendererAdversarialM3Test {

  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:1.2.3";

  private static InitPlan plan(Action... actions) {
    return new InitPlan(COORDINATE, false, List.of(actions));
  }

  @Test
  void renderOfAnEmptyPlanMatchesItsOwnJsonOrTextPlusDiffEitherWay() {
    InitPlan empty = plan();

    assertThat(PlanRenderer.render(empty, false))
        .isEqualTo(PlanRenderer.renderText(empty) + PlanRenderer.renderDiff(empty));
    assertThat(PlanRenderer.render(empty, true)).isEqualTo(PlanRenderer.renderJson(empty));
  }

  @Test
  void renderOfAReportWithOnlyRefusalsMatchesItsOwnJsonOrText(@TempDir Path dir) {
    ExecutionReport allRefused =
        PlanExecutor.execute(plan(new Action.Refuse(Path.of("AGENTS.md"), "two sections")), dir);

    assertThat(PlanRenderer.render(allRefused, false))
        .isEqualTo(PlanRenderer.renderText(allRefused));
    assertThat(PlanRenderer.render(allRefused, true))
        .isEqualTo(PlanRenderer.renderJson(allRefused));
  }
}
