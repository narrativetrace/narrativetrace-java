/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.OpenAiListing;
import java.util.List;

/**
 * Renders {@code plugin.json} for the OpenAI plugin directory (shared by ChatGPT and Codex): the
 * versionless text that is committed BUILD OUTPUT ({@link #render(OpenAiListing)}, pinned by {@code
 * RenderDriftTest}), and the packaged text a release ZIP carries ({@link #render(OpenAiListing,
 * String)}). Both go through one private builder, so the two differ only by the {@code "version"}
 * line.
 *
 * <p>Field names and the manifest shape follow the vendor's own plugin schema (<a
 * href="https://agent-plugins.org/schemas/1.0.0/plugin.schema.json">agent-plugins.org/schemas/
 * 1.0.0/plugin.schema.json</a>) and its {@code extensions.com.openai.interface} listing fields,
 * verified against the vendor's docs 2026-10-17. Skips every key this repository does not use
 * today: no {@code hooks} or {@code apps} (this plugin is skills-only), no {@code onboardingSkill},
 * no {@code requires_local_executor} (the desktop-only declaration waits on a cloud trial), and no
 * {@code privacyPolicyURL}/{@code termsOfServiceURL} (narrativetrace.ai carries neither page yet).
 */
public final class OpenAiPluginJsonRenderer {

  private static final String SCHEMA_URL =
      "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json";

  /**
   * Appended to {@code pluginDescription} for the directory's {@code longDescription}: states the
   * environment a skill-only plugin needs, and bounds the claim to what a run actually showed —
   * never more, the same rule {@code narrativetrace-verify} and {@code narrativetrace-explain} hold
   * their own reports to.
   */
  private static final String ENVIRONMENT_SENTENCE =
      "It reads what one run actually did, and needs a JDK, this project's build tool, and"
          + " network access for the first dependency fetch.";

  private OpenAiPluginJsonRenderer() {}

  /** The committed, versionless text — the installed version IS the release that shipped it. */
  public static String render(OpenAiListing listing) {
    return build(listing, null);
  }

  /** The packaged text a release ZIP carries, with the release version inserted. */
  public static String render(OpenAiListing listing, String version) {
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
    return build(listing, version);
  }

  private static String build(OpenAiListing listing, String version) {
    StringBuilder out = new StringBuilder("{\n");
    out.append("  \"$schema\": ").append(JsonText.quote(SCHEMA_URL)).append(",\n");
    out.append("  \"name\": ").append(JsonText.quote(listing.base().name())).append(",\n");
    if (version != null) {
      out.append("  \"version\": ").append(JsonText.quote(version)).append(",\n");
    }
    out.append("  \"description\": ")
        .append(JsonText.quote(listing.base().pluginDescription()))
        .append(",\n");
    appendAuthor(out, listing);
    out.append("  \"homepage\": ").append(JsonText.quote(listing.base().homepage())).append(",\n");
    out.append("  \"license\": ").append(JsonText.quote(listing.base().license())).append(",\n");
    out.append("  \"keywords\": ").append(array(listing.base().keywords())).append(",\n");
    appendExtensions(out, listing);
    return out.append("}\n").toString();
  }

  private static void appendAuthor(StringBuilder out, OpenAiListing listing) {
    out.append("  \"author\": {\n");
    out.append("    \"name\": ").append(JsonText.quote(listing.base().owner().name())).append('\n');
    out.append("  },\n");
  }

  private static void appendExtensions(StringBuilder out, OpenAiListing listing) {
    out.append("  \"extensions\": {\n");
    out.append("    \"com.openai\": {\n");
    out.append("      \"interface\": {\n");
    appendInterfaceFields(out, listing);
    out.append("      }\n");
    out.append("    }\n");
    out.append("  }\n");
  }

  private static void appendInterfaceFields(StringBuilder out, OpenAiListing listing) {
    String longDescription = listing.base().pluginDescription() + " " + ENVIRONMENT_SENTENCE;
    field(out, "displayName", listing.displayName());
    field(out, "shortDescription", listing.shortDescription());
    field(out, "longDescription", longDescription);
    field(out, "developerName", listing.base().owner().name());
    field(out, "category", listing.category());
    out.append("        \"capabilities\": ").append(array(listing.capabilities())).append(",\n");
    out.append("        \"defaultPrompt\": ").append(array(listing.defaultPrompts())).append(",\n");
    field(out, "brandColor", listing.brandColor());
    field(out, "websiteURL", listing.websiteUrl());
    field(out, "logo", "./assets/logo.png");
    out.append("        \"composerIcon\": ")
        .append(JsonText.quote("./assets/composer-icon.png"))
        .append('\n');
  }

  private static void field(StringBuilder out, String name, String value) {
    out.append("        \"")
        .append(name)
        .append("\": ")
        .append(JsonText.quote(value))
        .append(",\n");
  }

  private static String array(List<String> values) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      out.append(i == 0 ? "" : ", ").append(JsonText.quote(values.get(i)));
    }
    return out.append(']').toString();
  }
}
