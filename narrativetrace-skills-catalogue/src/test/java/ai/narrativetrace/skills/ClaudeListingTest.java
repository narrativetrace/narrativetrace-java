/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import org.junit.jupiter.api.Test;

/** The Anthropic directory's listing fields, beside the vendor-neutral listing they extend. */
class ClaudeListingTest {

  private final ClaudeListing listing = CatalogueIndex.CLAUDE_DIRECTORY;

  @Test
  void drawsFromTheSameMarketplaceListingAsEveryOtherRegistry() {
    assertThat(listing.base()).isSameAs(CatalogueIndex.MARKETPLACE);
  }

  @Test
  void usesTheDisplayNameTheOpenAiListingUses() {
    assertThat(listing.displayName())
        .isEqualTo("NarrativeTrace for Java")
        .isEqualTo(CatalogueIndex.OPENAI_DIRECTORY.displayName());
  }

  @Test
  void pointsSupportPrivacyAndDocumentationAtTheirPages() {
    assertThat(listing.supportUrl())
        .isEqualTo("https://github.com/narrativetrace/narrativetrace-java/issues");
    assertThat(listing.privacyPolicyUrl()).isEqualTo("https://narrativetrace.ai/privacy");
    assertThat(listing.documentationUrl())
        .isEqualTo("https://github.com/narrativetrace/narrativetrace-java/tree/main/documentation");
  }
}
