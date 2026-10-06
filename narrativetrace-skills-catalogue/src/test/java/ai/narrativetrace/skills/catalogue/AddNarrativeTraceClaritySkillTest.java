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
import org.junit.jupiter.api.Test;

class AddNarrativeTraceClaritySkillTest {

  @Test
  void declaresTheRealScanAndArtifactChecks() {
    Skill skill = AddNarrativeTraceClaritySkill.build();

    assertThat(skill.canonicalName()).isEqualTo("add-narrativetrace-clarity");
    assertThat(skill.commandStrings())
        .contains(
            ClarityCommands.CLEAN_STATIC_SCAN,
            ClarityCommands.FIND_JSON_REPORT,
            ClarityCommands.FIND_MARKDOWN_REPORT);
    assertThat(skill.description())
        .contains("static naming report")
        .contains("narrativetrace-doctor");
    assertThat(skill.never())
        .extracting(rule -> rule.rule())
        .anyMatch(rule -> rule.contains("lower"))
        .anyMatch(rule -> rule.contains("glossary"));
  }
}
