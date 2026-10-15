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
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPluginJsonRendererTest {

  private static MarketplaceListing listing() {
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

  @Test
  void pluginManifestCarriesTheListingAndPointsAtTheOpenStandardSkillsAndTheOneRule() {
    assertThat(CursorPluginJsonRenderer.renderPlugin(listing()))
        .isEqualTo(
            "{\n"
                + "  \"name\": \"narrativetrace-x\",\n"
                + "  \"description\": \"The plugin description.\",\n"
                + "  \"author\": {\n"
                + "    \"name\": \"NarrativeTrace\"\n"
                + "  },\n"
                + "  \"license\": \"Apache-2.0\",\n"
                + "  \"homepage\": \"https://narrativetrace.ai\",\n"
                + "  \"keywords\": [\"tracing\", \"agent-skills\"],\n"
                + "  \"skills\": \"./.agents/skills/\",\n"
                + "  \"rules\": \"./.cursor/rules/narrativetrace.mdc\"\n"
                + "}\n");
  }

  @Test
  void pluginManifestCarriesNoCommandsHooksMcpOrVersion() {
    assertThat(CursorPluginJsonRenderer.renderPlugin(listing()))
        .doesNotContain("\"commands\"")
        .doesNotContain("\"hooks\"")
        .doesNotContain("\"mcpServers\"")
        .doesNotContain("\"agents\"")
        .doesNotContain("\"version\"");
  }

  @Test
  void marketplaceListsTheOnePluginRootedAtTheRepository() {
    assertThat(CursorPluginJsonRenderer.renderMarketplace(listing()))
        .isEqualTo(
            "{\n"
                + "  \"name\": \"narrativetrace-x\",\n"
                + "  \"owner\": {\n"
                + "    \"name\": \"NarrativeTrace\"\n"
                + "  },\n"
                + "  \"metadata\": {\n"
                + "    \"description\": \"The marketplace description.\"\n"
                + "  },\n"
                + "  \"plugins\": [\n"
                + "    {\n"
                + "      \"name\": \"narrativetrace-x\",\n"
                + "      \"source\": \".\",\n"
                + "      \"description\": \"The plugin description.\",\n"
                + "      \"license\": \"Apache-2.0\",\n"
                + "      \"homepage\": \"https://narrativetrace.ai\",\n"
                + "      \"keywords\": [\"tracing\", \"agent-skills\"]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");
  }

  @Test
  void bothRenderByteIdenticallyEveryTime() {
    assertThat(CursorPluginJsonRenderer.renderPlugin(listing()))
        .isEqualTo(CursorPluginJsonRenderer.renderPlugin(listing()));
    assertThat(CursorPluginJsonRenderer.renderMarketplace(listing()))
        .isEqualTo(CursorPluginJsonRenderer.renderMarketplace(listing()));
  }

  @Test
  void escapesEveryCharacterJsonCannotCarryRaw() {
    MarketplaceListing hostile =
        new MarketplaceListing(
            "narrativetrace-x",
            new MarketplaceListing.Owner("Narrative\"Trace", "https://narrativetrace.ai"),
            "A \"quoted\" \\ name\twith\na newline and a \u0001 control char.",
            "d\"",
            "./.claude",
            "Apache-2.0",
            "https://narrativetrace.ai",
            List.of("a\"b"));

    String plugin = CursorPluginJsonRenderer.renderPlugin(hostile);
    String marketplace = CursorPluginJsonRenderer.renderMarketplace(hostile);

    assertThat(plugin).contains("\"Narrative\\\"Trace\"").contains("\"d\\\"\"").contains("a\\\"b");
    assertThat(marketplace)
        .contains("A \\\"quoted\\\" \\\\ name\\twith\\na newline and a \\u0001 control char.");
    assertThat(plugin + marketplace).doesNotContain("\u0001");
  }

  @Test
  void theRepositoryPathsAreOnePlace() {
    assertThat(RenderPaths.cursorPluginJson(java.nio.file.Path.of("r")).toString())
        .isEqualTo("r/.cursor-plugin/plugin.json");
    assertThat(RenderPaths.cursorMarketplaceJson(java.nio.file.Path.of("r")).toString())
        .isEqualTo("r/.cursor-plugin/marketplace.json");
    assertThat(RenderPaths.cursorRule(java.nio.file.Path.of("r")).toString())
        .isEqualTo("r/.cursor/rules/narrativetrace.mdc");
  }
}
