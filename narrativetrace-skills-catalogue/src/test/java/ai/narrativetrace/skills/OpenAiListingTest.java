/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The OpenAI directory's own listing fields beside the vendor-neutral {@link MarketplaceListing}:
 * {@code displayName} and {@code shortDescription} carry the directory's 30-character ceiling [S],
 * verified against the pages 2026-10-17 — a longer value never reaches a render.
 */
class OpenAiListingTest {

  private static MarketplaceListing base() {
    return new MarketplaceListing(
        "narrativetrace-x",
        new MarketplaceListing.Owner("NarrativeTrace", "https://narrativetrace.ai"),
        "The marketplace description.",
        "The plugin description.",
        "./.claude",
        "Apache-2.0",
        "https://narrativetrace.ai",
        List.of("tracing", "agent-skills"));
  }

  private static OpenAiListing listing(String displayName, String shortDescription) {
    return new OpenAiListing(
        base(),
        displayName,
        shortDescription,
        "Developer Tools",
        List.of("Read", "Write"),
        List.of("add narrative tracing to my service"),
        "#4A4A4A",
        "https://narrativetrace.ai");
  }

  @Test
  void carriesTheBaseListingAndTheOpenAiOnlyFields() {
    OpenAiListing listing = listing("NarrativeTrace for X", "NarrativeTrace skills for X");

    assertThat(listing.base()).isEqualTo(base());
    assertThat(listing.displayName()).isEqualTo("NarrativeTrace for X");
    assertThat(listing.shortDescription()).isEqualTo("NarrativeTrace skills for X");
    assertThat(listing.category()).isEqualTo("Developer Tools");
    assertThat(listing.capabilities()).containsExactly("Read", "Write");
    assertThat(listing.defaultPrompts()).containsExactly("add narrative tracing to my service");
    assertThat(listing.brandColor()).isEqualTo("#4A4A4A");
    assertThat(listing.websiteUrl()).isEqualTo("https://narrativetrace.ai");
  }

  @Test
  void aThirtyCharacterDisplayNameIsAccepted() {
    String thirty = "x".repeat(30);

    assertThat(listing(thirty, "short").displayName()).hasSize(30);
  }

  @Test
  void aThirtyOneCharacterDisplayNameIsRejected() {
    String tooLong = "x".repeat(31);

    assertThatThrownBy(() -> listing(tooLong, "short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("displayName")
        .hasMessageContaining("30");
  }

  @Test
  void aThirtyCharacterShortDescriptionIsAccepted() {
    String thirty = "x".repeat(30);

    assertThat(listing("name", thirty).shortDescription()).hasSize(30);
  }

  @Test
  void aThirtyOneCharacterShortDescriptionIsRejected() {
    String tooLong = "x".repeat(31);

    assertThatThrownBy(() -> listing("name", tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shortDescription")
        .hasMessageContaining("30");
  }

  @Test
  void capabilitiesAndDefaultPromptsAreDefensivelyCopied() {
    var capabilities = new java.util.ArrayList<>(List.of("Read"));
    var listing =
        new OpenAiListing(
            base(),
            "name",
            "short",
            "Developer Tools",
            capabilities,
            new java.util.ArrayList<>(List.of("prompt")),
            "#4A4A4A",
            "https://narrativetrace.ai");

    capabilities.add("Write");

    assertThat(listing.capabilities()).containsExactly("Read");
  }
}
