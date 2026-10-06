/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

import java.util.List;

/**
 * This runtime's registry identity: everything a plugin-marketplace listing needs that is not a
 * skill page. One listing per runtime repository — the marketplace and its single plugin share the
 * name, because a marketplace name is unique per user and two runtimes' plugins carry the same
 * skill names.
 *
 * <p>No version, deliberately: with a relative-path plugin source the installed version IS the
 * repository's commit, so a version literal here would drift on every release and say nothing the
 * commit does not.
 *
 * @param name the marketplace identifier AND the plugin's — what a user types after {@code @} when
 *     installing, and (with no {@code plugin.json} in the tree) the plugin's manifest name too
 * @param owner who maintains it, shown in the listing
 * @param description the marketplace's own line, shown when browsing it
 * @param pluginDescription the plugin entry's line, shown in the plugin list and details
 * @param pluginSource the plugin's directory, relative to the marketplace root (the repository
 *     root, the directory holding {@code .claude-plugin/}) — the rendered pages' own root, so the
 *     plugin carries the skills and nothing else of the repository
 * @param license SPDX identifier of the pages the plugin ships, which may differ from the
 *     repository root's own licence
 * @param homepage where a reader goes to learn what this is
 * @param keywords free-form search terms for the listing
 */
public record MarketplaceListing(
    String name,
    Owner owner,
    String description,
    String pluginDescription,
    String pluginSource,
    String license,
    String homepage,
    List<String> keywords) {

  /** The maintainer behind the listing: a name, and where to read about them. */
  public record Owner(String name, String url) {}

  public MarketplaceListing {
    keywords = List.copyOf(keywords);
  }
}
