/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pin every trace-reading skill renders, in the steps both skills share. */
class BaselinePinTest {

  @Test
  void theApprovalRunIsTheWholeSuiteBecauseApprovalModeComparesEveryTracedTest() {
    SkillStep run =
        BaselinePin.steps().stream()
            .filter(
                s -> s.title().equals("Run the suite in approval mode and show every .received.nt"))
            .findFirst()
            .orElseThrow();

    assertThat(run.body())
        .isEqualTo(
            new StepBody.CommandStep(
                List.of(VerifyCommands.RUN_THE_SUITE, VerifyCommands.FIND_RECEIVED)));
    assertThat(run.verify())
        .contains("every traced test")
        .contains("not only the one this skill ran");
  }

  @Test
  void thePromotionLeavesTheSuiteGreen() {
    SkillStep promote = BaselinePin.steps().get(BaselinePin.steps().size() - 1);

    assertThat(promote.verify()).contains("the suite is green again after the promotion");
  }
}
