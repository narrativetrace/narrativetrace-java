/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.OpenAiListing;
import ai.narrativetrace.skills.Skill;
import java.util.ArrayList;
import java.util.List;

/**
 * The OpenAI plugin package's file set, sorted: {@code plugin.json}, one {@code
 * skills/<name>/SKILL.md} per catalogue entry, and the two listing assets. {@code
 * checkOpenAiPluginZip} asserts the built ZIP's entries EQUAL this list — derived from the build,
 * never a glob (release rule 5) — so a skill added to the catalogue without a packaging change
 * fails the gate instead of shipping an incomplete ZIP.
 */
public final class OpenAiPackageLayout {

  /** The ZIP-entry path of the plugin's logo, relative to the package root. */
  public static final String LOGO_ENTRY = "assets/logo.png";

  /** The ZIP-entry path of the plugin's composer icon, relative to the package root. */
  public static final String COMPOSER_ICON_ENTRY = "assets/composer-icon.png";

  private static final String PLUGIN_JSON_ENTRY = "plugin.json";

  private OpenAiPackageLayout() {}

  public static List<String> entries(List<Skill> skills, OpenAiListing listing) {
    if (listing == null) {
      throw new IllegalArgumentException("entries requires a listing");
    }
    List<String> entries = new ArrayList<>();
    entries.add(PLUGIN_JSON_ENTRY);
    for (Skill skill : skills) {
      entries.add("skills/" + skill.canonicalName() + "/SKILL.md");
    }
    entries.add(LOGO_ENTRY);
    entries.add(COMPOSER_ICON_ENTRY);
    entries.sort(String::compareTo);
    return List.copyOf(entries);
  }
}
