/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.OpenAiListing;
import org.junit.jupiter.api.Test;

/** How this repository presents its catalogue to the OpenAI plugin directory. */
class CatalogueOpenAiDirectoryTest {

  @Test
  void isComposedFromTheSameMarketplaceListing() {
    assertThat(CatalogueIndex.OPENAI_DIRECTORY.base()).isEqualTo(CatalogueIndex.MARKETPLACE);
  }

  @Test
  void theDisplayNameAndShortDescriptionFitTheDirectorysCeiling() {
    OpenAiListing directory = CatalogueIndex.OPENAI_DIRECTORY;

    assertThat(directory.displayName())
        .isEqualTo("NarrativeTrace for Java")
        .hasSizeLessThanOrEqualTo(OpenAiListing.MAX_LISTING_FIELD_LENGTH);
    assertThat(directory.shortDescription())
        .hasSizeLessThanOrEqualTo(OpenAiListing.MAX_LISTING_FIELD_LENGTH);
  }

  @Test
  void theDefaultPromptsAreDrawnFromTheCatalogueSOwnTriggerPhrases() {
    assertThat(CatalogueIndex.OPENAI_DIRECTORY.defaultPrompts())
        .allSatisfy(
            prompt ->
                assertThat(
                        CatalogueIndex.ALL.stream()
                            .anyMatch(skill -> skill.description().contains("'" + prompt + "'")))
                    .as("'%s' must be one of a skill's own trigger phrases", prompt)
                    .isTrue());
  }

  @Test
  void websiteUrlMatchesTheBaseListingsHomepage() {
    assertThat(CatalogueIndex.OPENAI_DIRECTORY.websiteUrl())
        .isEqualTo(CatalogueIndex.MARKETPLACE.homepage());
  }
}
