/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiPackageLayoutTest {

  @Test
  void listsThePluginManifestEverySkillPageAndTheTwoAssets() {
    var entries = OpenAiPackageLayout.entries(CatalogueIndex.ALL, CatalogueIndex.OPENAI_DIRECTORY);

    assertThat(entries)
        .contains("plugin.json", "assets/logo.png", "assets/composer-icon.png")
        .hasSize(CatalogueIndex.ALL.size() + 3);
    for (var skill : CatalogueIndex.ALL) {
      assertThat(entries).contains("skills/" + skill.canonicalName() + "/SKILL.md");
    }
  }

  @Test
  void entriesAreSorted() {
    var entries = OpenAiPackageLayout.entries(CatalogueIndex.ALL, CatalogueIndex.OPENAI_DIRECTORY);

    assertThat(entries).isSortedAccordingTo(String::compareTo);
  }

  @Test
  void rejectsANullListing() {
    assertThatThrownBy(() -> OpenAiPackageLayout.entries(CatalogueIndex.ALL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anEmptySkillListStillCarriesThePluginAndTheAssets() {
    var entries = OpenAiPackageLayout.entries(List.of(), CatalogueIndex.OPENAI_DIRECTORY);

    assertThat(entries)
        .containsExactly("assets/composer-icon.png", "assets/logo.png", "plugin.json");
  }
}
