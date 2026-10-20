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

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.OpenAiListing;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiPluginJsonRendererTest {

  private static OpenAiListing listing() {
    return new OpenAiListing(
        new MarketplaceListing(
            "narrativetrace-x",
            new MarketplaceListing.Owner("NarrativeTrace", "https://narrativetrace.ai"),
            "The marketplace description.",
            "The plugin description.",
            "./.claude",
            "Apache-2.0",
            "https://narrativetrace.ai",
            List.of("tracing", "agent-skills")),
        "NarrativeTrace for X",
        "NarrativeTrace skills for X",
        "Developer Tools",
        List.of("Read", "Write"),
        List.of("do the thing", "do the other thing"),
        "#4A4A4A",
        "https://narrativetrace.ai");
  }

  @Test
  void theCommittedRenderCarriesEveryMappedFieldAndNoVersion() {
    String json = OpenAiPluginJsonRenderer.render(listing());

    assertThat(json)
        .contains("\"$schema\": \"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\"")
        .contains("\"name\": \"narrativetrace-x\"")
        .contains("\"description\": \"The plugin description.\"")
        .contains("\"author\": {")
        .contains("\"name\": \"NarrativeTrace\"")
        .contains("\"homepage\": \"https://narrativetrace.ai\"")
        .contains("\"license\": \"Apache-2.0\"")
        .contains("\"keywords\": [\"tracing\", \"agent-skills\"]")
        .contains("\"extensions\": {")
        .contains("\"com.openai\": {")
        .contains("\"interface\": {")
        .contains("\"displayName\": \"NarrativeTrace for X\"")
        .contains("\"shortDescription\": \"NarrativeTrace skills for X\"")
        .contains("\"longDescription\": \"The plugin description.")
        .contains("\"developerName\": \"NarrativeTrace\"")
        .contains("\"category\": \"Developer Tools\"")
        .contains("\"capabilities\": [\"Read\", \"Write\"]")
        .contains("\"defaultPrompt\": [\"do the thing\", \"do the other thing\"]")
        .contains("\"brandColor\": \"#4A4A4A\"")
        .contains("\"websiteURL\": \"https://narrativetrace.ai\"")
        .contains("\"logo\": \"./assets/logo.png\"")
        .contains("\"composerIcon\": \"./assets/composer-icon.png\"")
        .doesNotContain("\"version\"");
  }

  @Test
  void thePackagedRenderAddsOnlyTheVersionLine() {
    String committed = OpenAiPluginJsonRenderer.render(listing());
    String packaged = OpenAiPluginJsonRenderer.render(listing(), "1.2.3");

    assertThat(packaged).contains("\"version\": \"1.2.3\"");
    assertThat(packaged.replace("  \"version\": \"1.2.3\",\n", "")).isEqualTo(committed);
  }

  @Test
  void rejectsABlankVersion() {
    assertThatThrownBy(() -> OpenAiPluginJsonRenderer.render(listing(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void carriesNoHooksAppsOnboardingSkillOrLocalExecutorKeys() {
    String json = OpenAiPluginJsonRenderer.render(listing(), "1.2.3");

    assertThat(json)
        .doesNotContain("\"hooks\"")
        .doesNotContain("\"apps\"")
        .doesNotContain("\"onboardingSkill\"")
        .doesNotContain("requires_local_executor")
        .doesNotContain("\"privacyPolicyURL\"")
        .doesNotContain("\"termsOfServiceURL\"");
  }

  @Test
  void bothFlavoursRenderByteIdenticallyEveryTime() {
    assertThat(OpenAiPluginJsonRenderer.render(listing()))
        .isEqualTo(OpenAiPluginJsonRenderer.render(listing()));
    assertThat(OpenAiPluginJsonRenderer.render(listing(), "1.2.3"))
        .isEqualTo(OpenAiPluginJsonRenderer.render(listing(), "1.2.3"));
  }

  @Test
  void theLongDescriptionNeverClaimsMoreThanWhatOneRunShowed() {
    String json = OpenAiPluginJsonRenderer.render(listing());

    assertThat(json).contains("reads what one run actually did");
  }

  @Test
  void escapesEveryCharacterJsonCannotCarryRaw() {
    OpenAiListing hostile =
        new OpenAiListing(
            new MarketplaceListing(
                "narrativetrace-x",
                new MarketplaceListing.Owner("Narrative\"Trace", "https://narrativetrace.ai"),
                "A \"quoted\" \\ name\twith\na newline and a  control char.",
                "d\"",
                "./.claude",
                "Apache-2.0",
                "https://narrativetrace.ai",
                List.of("a\"b")),
            "name",
            "short",
            "cat\"egory",
            List.of("Re\"ad"),
            List.of("prom\"pt"),
            "#000",
            "https://narrativetrace.ai");

    String json = OpenAiPluginJsonRenderer.render(hostile);

    assertThat(json)
        .contains("\"Narrative\\\"Trace\"")
        .contains("\"d\\\"\"")
        .contains("a\\\"b")
        .contains("cat\\\"egory")
        .contains("Re\\\"ad")
        .contains("prom\\\"pt")
        .doesNotContain("");
  }
}
