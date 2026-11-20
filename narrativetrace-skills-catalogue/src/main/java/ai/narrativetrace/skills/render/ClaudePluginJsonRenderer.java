/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.ClaudeListing;
import ai.narrativetrace.skills.MarketplaceListing;

/**
 * Renders {@code .claude/.claude-plugin/plugin.json}: the manifest of the plugin the root
 * marketplace file lists, kept INSIDE the plugin folder where Claude Code and Anthropic's plugin
 * directory read it. Without a manifest the plugin is named after its folder, {@code .claude}, a
 * reserved name the directory refuses.
 *
 * <p>Keys are the ones the manifest reference documents ({@code name}, {@code description}, {@code
 * author}, {@code homepage}, {@code repository}, {@code license}, {@code keywords}) plus the
 * directory listing fields the vendor reads from the manifest only ({@code displayName}, {@code
 * supportUrl}, {@code privacyPolicyUrl}, {@code documentationUrl}, {@code icon}). Every shared
 * value comes from the same {@link MarketplaceListing} {@link MarketplaceJsonRenderer} renders, so
 * the manifest and the marketplace entry cannot disagree about the plugin (the entry's {@code
 * strict} defaults to true, so both are read and the manifest's metadata is the plugin's own).
 *
 * <p>No {@code version} key, deliberately: a relative-path plugin's version is the commit, and a
 * version literal here would make every release rewrite a folder whose skills did not change.
 * Committed BUILD OUTPUT, pinned by {@code RenderDriftTest}.
 */
public final class ClaudePluginJsonRenderer {

  /**
   * Where the manifest finds its icon, relative to the plugin root (the {@code .claude/} folder).
   */
  static final String ICON_PATH = "./.claude-plugin/icon.png";

  private ClaudePluginJsonRenderer() {}

  public static String render(ClaudeListing directory) {
    MarketplaceListing listing = directory.base();
    StringBuilder out = new StringBuilder("{\n");
    JsonText.appendManifestIdentity(out, listing);
    out.append("  \"homepage\": ").append(JsonText.quote(listing.homepage())).append(",\n");
    out.append("  \"repository\": ").append(JsonText.quote(listing.repository())).append(",\n");
    out.append("  \"license\": ").append(JsonText.quote(listing.license())).append(",\n");
    out.append("  \"keywords\": ").append(JsonText.array(listing.keywords())).append(",\n");
    out.append("  \"displayName\": ").append(JsonText.quote(directory.displayName())).append(",\n");
    out.append("  \"supportUrl\": ").append(JsonText.quote(directory.supportUrl())).append(",\n");
    out.append("  \"privacyPolicyUrl\": ")
        .append(JsonText.quote(directory.privacyPolicyUrl()))
        .append(",\n");
    out.append("  \"documentationUrl\": ")
        .append(JsonText.quote(directory.documentationUrl()))
        .append(",\n");
    out.append("  \"icon\": ").append(JsonText.quote(ICON_PATH)).append('\n');
    return out.append("}\n").toString();
  }
}
