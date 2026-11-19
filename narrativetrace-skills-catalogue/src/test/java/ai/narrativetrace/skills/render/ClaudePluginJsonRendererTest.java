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

class ClaudePluginJsonRendererTest {

  private static MarketplaceListing listing() {
    return new MarketplaceListing(
        "narrativetrace-x",
        new MarketplaceListing.Owner("NarrativeTrace", "https://narrativetrace.ai"),
        "The marketplace description.",
        "The plugin description.",
        "./.claude",
        "Apache-2.0",
        "https://narrativetrace.ai",
        "https://github.com/narrativetrace/narrativetrace-x",
        List.of("tracing", "agent-skills"));
  }

  @Test
  void rendersTheManifestFromTheListingAlone() {
    assertThat(ClaudePluginJsonRenderer.render(listing()))
        .isEqualTo(
            "{\n"
                + "  \"name\": \"narrativetrace-x\",\n"
                + "  \"description\": \"The plugin description.\",\n"
                + "  \"author\": {\n"
                + "    \"name\": \"NarrativeTrace\"\n"
                + "  },\n"
                + "  \"homepage\": \"https://narrativetrace.ai\",\n"
                + "  \"repository\": \"https://github.com/narrativetrace/narrativetrace-x\",\n"
                + "  \"license\": \"Apache-2.0\",\n"
                + "  \"keywords\": [\"tracing\", \"agent-skills\"],\n"
                + "  \"icon\": \"./.claude-plugin/icon.png\"\n"
                + "}\n");
  }

  /**
   * Deliberately versionless: with a relative-path source the installed version is the commit, and
   * a release that does not change the skills must leave the folder byte-identical.
   */
  @Test
  void carriesNoVersionKeyAndNoVersionLiteral() {
    assertThat(ClaudePluginJsonRenderer.render(listing()))
        .doesNotContain("\"version\"")
        .doesNotContainPattern("\\d+\\.\\d+\\.\\d+");
  }

  @Test
  void rendersByteIdenticallyEveryTime() {
    assertThat(ClaudePluginJsonRenderer.render(listing()))
        .isEqualTo(ClaudePluginJsonRenderer.render(listing()));
  }

  @Test
  void escapesEveryCharacterJsonCannotCarryRaw() {
    MarketplaceListing hostile =
        new MarketplaceListing(
            "narrativetrace-x",
            new MarketplaceListing.Owner("Narrative \"Trace\"", "https://narrativetrace.ai"),
            "m",
            "A \"quoted\" \\ line\nwith a newline.",
            "./.claude",
            "Apache-2.0",
            "https://narrativetrace.ai",
            "https://example.com/r",
            List.of("k"));

    assertThat(ClaudePluginJsonRenderer.render(hostile))
        .contains("\"description\": \"A \\\"quoted\\\" \\\\ line\\nwith a newline.\"")
        .contains("\"name\": \"Narrative \\\"Trace\\\"\"");
  }
}
