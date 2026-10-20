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

/**
 * The doctor as a typed skill: read-only diagnosis, plus the wording-gap fix distinguishing a
 * plugin install (invisible to the doctor) from a project install (what reaches teammates and other
 * agents).
 */
class NarrativeTraceDoctorSkillTest {

  private final Skill skill = NarrativeTraceDoctorSkill.build();

  @Test
  void isNamedForTheCatalogueAndShipsInIt() {
    assertThat(skill.canonicalName()).isEqualTo("narrativetrace-doctor");
    assertThat(CatalogueIndex.ALL).contains(skill);
  }

  @Test
  void theDescriptionNamesTheProjectScopedCheck() {
    assertThat(skill.description())
        .contains(
            "whether the NarrativeTrace agent skills are installed in this project and current");
    assertThat(skill.descriptionFitsBudget()).isTrue();
  }

  @Test
  void theNonObviousTriggersExplainAPersonalPluginInstallIsInvisibleHere() {
    assertThat(skill.whenToUse())
        .contains("a user who installed these skills as a personal plugin in their agent")
        .contains("a plugin install is invisible to this check by design")
        .contains("only a project install reaches teammates and other agents");
  }
}
