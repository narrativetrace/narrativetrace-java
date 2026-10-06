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
import ai.narrativetrace.skills.render.MarketplaceJsonRenderer;
import ai.narrativetrace.skills.render.RenderPaths;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The shape of the marketplace listing this repository publishes, asserted on the typed source and
 * on its rendered JSON. The vendor requires {@code name}, {@code owner} and {@code plugins} at the
 * top level and {@code name} plus {@code source} in every entry; it rejects a relative source that
 * does not start with {@code ./} or that contains {@code ..}; and it treats an entry without a
 * {@code plugin.json} as the manifest itself, which is why the entry name has to be the plugin's
 * own name.
 */
class CatalogueMarketplaceTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("projectDir"));

  @Test
  void theMarketplaceAndItsOnePluginShareTheRuntimeSlugName() {
    assertThat(CatalogueIndex.MARKETPLACE.name()).isEqualTo("narrativetrace-java");
  }

  @Test
  void theRenderedFileCarriesEveryKeyTheVendorRequires() {
    String json = MarketplaceJsonRenderer.render(CatalogueIndex.MARKETPLACE);

    assertThat(json)
        .contains("\"name\": \"narrativetrace-java\"")
        .contains("\"owner\": {")
        .contains("\"plugins\": [")
        .contains("\"source\": \"./.claude\"");
  }

  @Test
  void theRenderedFileNamesTheOnePluginExactlyOnceBesidesTheMarketplaceItself() {
    String json = MarketplaceJsonRenderer.render(CatalogueIndex.MARKETPLACE);

    assertThat(json.split("\"name\": \"narrativetrace-java\"", -1))
        .as("the marketplace name and the plugin entry's name, and no third listing")
        .hasSize(3);
  }

  /** No version anywhere: the installed version is the commit a relative-path plugin came from. */
  @Test
  void theRenderedFileCarriesNoVersionKeyAndNoVersionLiteral() {
    String json = MarketplaceJsonRenderer.render(CatalogueIndex.MARKETPLACE);

    assertThat(json).doesNotContain("\"version\"").doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void thePluginSourceIsARelativePathTheVendorAccepts() {
    String source = CatalogueIndex.MARKETPLACE.pluginSource();

    assertThat(source).startsWith("./").doesNotContain("..").doesNotContain("\\");
  }

  @Test
  void thePluginSourceDirectoryHoldsEverySkillPageTheCatalogueDeclares() {
    Path pluginRoot = REPO_ROOT.resolve(CatalogueIndex.MARKETPLACE.pluginSource()).normalize();

    for (Skill skill : CatalogueIndex.ALL) {
      assertThat(RenderPaths.claudeSkillMd(REPO_ROOT, skill))
          .as("%s's page must live under the plugin source", skill.canonicalName())
          .exists()
          .isEqualTo(pluginRoot.resolve("skills/" + skill.canonicalName() + "/SKILL.md"));
    }
  }

  @Test
  void theListingDeclaresTheOpenLicenceThePagesShipUnder() {
    assertThat(CatalogueIndex.MARKETPLACE.license()).isEqualTo("Apache-2.0");
  }

  @Test
  void theListingCarriesSearchKeywordsAndAHomepage() {
    assertThat(CatalogueIndex.MARKETPLACE.keywords()).contains("narrativetrace", "java");
    assertThat(CatalogueIndex.MARKETPLACE.homepage()).startsWith("https://");
  }
}
