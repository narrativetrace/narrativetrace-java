/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The plugin folder carries its own {@code plugin.json}, and the root marketplace entry for the
 * same plugin carries display fields too. Claude Code combines the two (the entry's {@code strict}
 * defaults to true: the manifest loads and the entry appends components), and the directory reads
 * the manifest alone, so the two must say the same thing about the plugin or one of them is wrong
 * on some surface. Both come from the one {@link MarketplaceListing}; this pins that on the
 * committed files, so a hand edit of either fails here.
 */
class ClaudePluginAgreesWithMarketplaceTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  /** Keys that would add components to a plugin whose manifest is the source of its content. */
  private static final List<String> COMPONENT_KEYS =
      List.of(
          "commands",
          "agents",
          "skills",
          "hooks",
          "mcpServers",
          "outputStyles",
          "themes",
          "strict");

  private final MarketplaceListing listing = CatalogueIndex.MARKETPLACE;

  @Test
  void theManifestAndTheMarketplaceEntryAgreeOnEverySharedField() throws IOException {
    String manifest = Files.readString(RenderPaths.claudePluginJson(REPO_ROOT));
    String marketplace = Files.readString(RenderPaths.marketplaceJson(REPO_ROOT));

    for (String field :
        List.of(
            "\"name\": " + JsonText.quote(listing.name()),
            "\"description\": " + JsonText.quote(listing.pluginDescription()),
            "\"license\": " + JsonText.quote(listing.license()),
            "\"homepage\": " + JsonText.quote(listing.homepage()),
            "\"keywords\": " + keywords())) {
      assertThat(manifest).as("plugin.json").contains(field);
      assertThat(marketplace).as("marketplace.json").contains(field);
    }
  }

  @Test
  void theMarketplaceEntrySourcePointsAtTheFolderThatHoldsTheManifest() {
    assertThat(listing.pluginSource()).isEqualTo("./.claude");
    assertThat(RenderPaths.claudePluginJson(REPO_ROOT))
        .isEqualTo(
            REPO_ROOT
                .resolve(listing.pluginSource())
                .normalize()
                .resolve(".claude-plugin/plugin.json"));
  }

  @Test
  void theMarketplaceEntryAddsNoComponentsToTheManifestAndKeepsStrictAtItsDefault()
      throws IOException {
    String marketplace = Files.readString(RenderPaths.marketplaceJson(REPO_ROOT));

    for (String key : COMPONENT_KEYS) {
      assertThat(marketplace).as("marketplace entry key " + key).doesNotContain("\"" + key + "\"");
    }
  }

  @Test
  void theManifestNamesTheDirectoryListingFieldsOnlyInTheManifest() throws IOException {
    String manifest = Files.readString(RenderPaths.claudePluginJson(REPO_ROOT));
    String marketplace = Files.readString(RenderPaths.marketplaceJson(REPO_ROOT));

    assertThat(manifest).contains("\"icon\": \"./.claude-plugin/icon.png\"");
    assertThat(marketplace)
        .as("the vendor reports these as unknown in an entry")
        .doesNotContain("\"icon\"");
  }

  private String keywords() {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < listing.keywords().size(); i++) {
      out.append(i == 0 ? "" : ", ").append(JsonText.quote(listing.keywords().get(i)));
    }
    return out.append(']').toString();
  }
}
