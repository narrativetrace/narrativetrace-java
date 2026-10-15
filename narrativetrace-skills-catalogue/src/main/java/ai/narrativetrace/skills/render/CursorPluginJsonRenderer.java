/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.MarketplaceListing;

/**
 * Renders the Cursor plugin manifest pair — {@code .cursor-plugin/plugin.json} and {@code
 * .cursor-plugin/marketplace.json} — from the SAME {@link MarketplaceListing} that renders {@code
 * .claude-plugin/marketplace.json}.
 *
 * <p>INTENT: one catalogue, one listing, two vendors' files. The plugin is the repository root and
 * carries the skills as the open-standard {@code .agents/skills} directory Cursor reads natively
 * plus the one rule file that points at them — no commands, hooks, agents or MCP servers (Phase 8
 * D3). Committed BUILD OUTPUT like every render target; {@code RenderDriftTest} pins both files
 * against a fresh render.
 *
 * <p><b>@llmNote</b> Field names follow Cursor's plugin reference (verified 2026-10-10): {@code
 * name} is required, {@code author} takes {@code name}/{@code email} only, a {@code skills} or
 * {@code rules} path replaces folder discovery, and a marketplace entry's {@code source} is the
 * plugin directory relative to the repository root — {@code .} for a repository that is itself the
 * one plugin. No {@code version} key, for the reason {@link MarketplaceListing} states.
 */
public final class CursorPluginJsonRenderer {

  /** The skills directory the plugin points at: the open-standard flavour Cursor reads natively. */
  static final String SKILLS_PATH = "./.agents/skills/";

  /** The one rule the plugin carries, the same file {@code init} writes into a Cursor project. */
  static final String RULE_PATH = "./.cursor/rules/narrativetrace.mdc";

  private CursorPluginJsonRenderer() {}

  /** {@code .cursor-plugin/plugin.json}. */
  public static String renderPlugin(MarketplaceListing listing) {
    StringBuilder out = new StringBuilder("{\n");
    out.append("  \"name\": ").append(JsonText.quote(listing.name())).append(",\n");
    out.append("  \"description\": ")
        .append(JsonText.quote(listing.pluginDescription()))
        .append(",\n");
    out.append("  \"author\": {\n");
    out.append("    \"name\": ").append(JsonText.quote(listing.owner().name())).append('\n');
    out.append("  },\n");
    out.append("  \"license\": ").append(JsonText.quote(listing.license())).append(",\n");
    out.append("  \"homepage\": ").append(JsonText.quote(listing.homepage())).append(",\n");
    out.append("  \"keywords\": ").append(keywords(listing)).append(",\n");
    out.append("  \"skills\": ").append(JsonText.quote(SKILLS_PATH)).append(",\n");
    out.append("  \"rules\": ").append(JsonText.quote(RULE_PATH)).append('\n');
    return out.append("}\n").toString();
  }

  /** {@code .cursor-plugin/marketplace.json}. */
  public static String renderMarketplace(MarketplaceListing listing) {
    StringBuilder out = new StringBuilder("{\n");
    out.append("  \"name\": ").append(JsonText.quote(listing.name())).append(",\n");
    out.append("  \"owner\": {\n");
    out.append("    \"name\": ").append(JsonText.quote(listing.owner().name())).append('\n');
    out.append("  },\n");
    out.append("  \"metadata\": {\n");
    out.append("    \"description\": ").append(JsonText.quote(listing.description())).append('\n');
    out.append("  },\n");
    out.append("  \"plugins\": [\n");
    out.append("    {\n");
    out.append("      \"name\": ").append(JsonText.quote(listing.name())).append(",\n");
    out.append("      \"source\": \".\",\n");
    out.append("      \"description\": ")
        .append(JsonText.quote(listing.pluginDescription()))
        .append(",\n");
    out.append("      \"license\": ").append(JsonText.quote(listing.license())).append(",\n");
    out.append("      \"homepage\": ").append(JsonText.quote(listing.homepage())).append(",\n");
    out.append("      \"keywords\": ").append(keywords(listing)).append('\n');
    out.append("    }\n");
    return out.append("  ]\n}\n").toString();
  }

  private static String keywords(MarketplaceListing listing) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < listing.keywords().size(); i++) {
      out.append(i == 0 ? "" : ", ").append(JsonText.quote(listing.keywords().get(i)));
    }
    return out.append(']').toString();
  }
}
