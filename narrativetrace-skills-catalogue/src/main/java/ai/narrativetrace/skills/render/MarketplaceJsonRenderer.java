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
 * Renders {@code .claude-plugin/marketplace.json}: the file that makes this repository a plugin
 * marketplace carrying one plugin — the rendered Claude-flavour skill pages, rooted at the {@code
 * .claude/} directory they already sit in.
 *
 * <p>Committed BUILD OUTPUT, like every other render target: the typed {@link MarketplaceListing}
 * is the only place this text is written, and {@code RenderDriftTest} pins the committed file
 * against a fresh render.
 */
public final class MarketplaceJsonRenderer {

  private MarketplaceJsonRenderer() {}

  public static String render(MarketplaceListing listing) {
    StringBuilder out = new StringBuilder();
    out.append("{\n");
    out.append("  \"name\": ").append(JsonText.quote(listing.name())).append(",\n");
    out.append("  \"owner\": {\n");
    out.append("    \"name\": ").append(JsonText.quote(listing.owner().name())).append(",\n");
    out.append("    \"url\": ").append(JsonText.quote(listing.owner().url())).append('\n');
    out.append("  },\n");
    out.append("  \"description\": ").append(JsonText.quote(listing.description())).append(",\n");
    out.append("  \"plugins\": [\n");
    appendPluginEntry(out, listing);
    out.append("  ]\n}\n");
    return out.toString();
  }

  /**
   * The single plugin entry. {@code name} and {@code source} are the two the vendor requires; the
   * rest are the display fields a listing is read by. No {@code version} key: see {@link
   * MarketplaceListing}.
   */
  private static void appendPluginEntry(StringBuilder out, MarketplaceListing listing) {
    out.append("    {\n");
    out.append("      \"name\": ").append(JsonText.quote(listing.name())).append(",\n");
    out.append("      \"source\": ").append(JsonText.quote(listing.pluginSource())).append(",\n");
    out.append("      \"description\": ")
        .append(JsonText.quote(listing.pluginDescription()))
        .append(",\n");
    out.append("      \"license\": ").append(JsonText.quote(listing.license())).append(",\n");
    out.append("      \"homepage\": ").append(JsonText.quote(listing.homepage())).append(",\n");
    out.append("      \"keywords\": ").append(keywords(listing)).append('\n');
    out.append("    }\n");
  }

  /** The keywords as a JSON array on one line — a short, flat list reads better unwrapped. */
  private static String keywords(MarketplaceListing listing) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < listing.keywords().size(); i++) {
      out.append(i == 0 ? "" : ", ").append(JsonText.quote(listing.keywords().get(i)));
    }
    return out.append(']').toString();
  }
}
