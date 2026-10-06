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

class MarketplaceJsonRendererTest {

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
  void namesTheMarketplaceAndItsOwner() {
    String json = MarketplaceJsonRenderer.render(listing());

    assertThat(json)
        .startsWith(
            "{\n"
                + "  \"name\": \"narrativetrace-x\",\n"
                + "  \"owner\": {\n"
                + "    \"name\": \"NarrativeTrace\",\n"
                + "    \"url\": \"https://narrativetrace.ai\"\n"
                + "  },\n");
  }

  @Test
  void carriesOneRelativePathPluginEntryRootedAtTheRenderedPages() {
    String json = MarketplaceJsonRenderer.render(listing());

    assertThat(json)
        .endsWith(
            "  \"plugins\": [\n"
                + "    {\n"
                + "      \"name\": \"narrativetrace-x\",\n"
                + "      \"source\": \"./.claude\",\n"
                + "      \"description\": \"The plugin description.\",\n"
                + "      \"license\": \"Apache-2.0\",\n"
                + "      \"homepage\": \"https://narrativetrace.ai\",\n"
                + "      \"keywords\": [\"tracing\", \"agent-skills\"]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");
  }

  @Test
  void rendersByteIdenticallyEveryTime() {
    assertThat(MarketplaceJsonRenderer.render(listing()))
        .isEqualTo(MarketplaceJsonRenderer.render(listing()));
  }

  @Test
  void carriesNoVersionKeyAnywhere() {
    assertThat(MarketplaceJsonRenderer.render(listing())).doesNotContain("\"version\"");
  }

  @Test
  void escapesEveryCharacterJsonCannotCarryRaw() {
    MarketplaceListing hostile =
        new MarketplaceListing(
            "narrativetrace-x",
            new MarketplaceListing.Owner("NarrativeTrace", "https://narrativetrace.ai"),
            "A \"quoted\" \\ name\twith\na newline and a \u0001 control char.",
            "d",
            "./.claude",
            "Apache-2.0",
            "https://narrativetrace.ai",
            List.of("tracing"));

    assertThat(MarketplaceJsonRenderer.render(hostile))
        .contains(
            "\"description\": \"A \\\"quoted\\\" \\\\ name\\twith\\na newline and a"
                + " \\u0001 control char.\"");
  }
}
