/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.Skill;
import ai.narrativetrace.skills.SkillStep;
import org.junit.jupiter.api.Test;

class NarrativeTraceFeedbackSkillTest {

  @Test
  void theApprovalStepSaysThatShowingTheUrlIsTheFiling() {
    Skill skill = NarrativeTraceFeedbackSkill.build();

    SkillStep approval =
        skill.steps().stream()
            .filter(step -> step.title().startsWith("Ask once"))
            .findFirst()
            .orElseThrow();

    assertThat(approval.verify())
        .contains(
            "Do not print the issue URL or run gh before the user says yes"
                + " — showing the URL is the filing.");
  }
}
