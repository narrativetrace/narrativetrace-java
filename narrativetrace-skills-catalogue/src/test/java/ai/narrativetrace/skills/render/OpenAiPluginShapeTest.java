/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks the committed {@code plugin.json} against the vendor's own plugin schema shape — CI never
 * fetches the schema itself (the portal's own validation on upload is the by-hand check), so this
 * is the offline stand-in: every top-level key is one the schema allows, the two required keys are
 * present, {@code name} matches the schema's own pattern, and the skills-only rules (no {@code
 * version} in the committed file, no {@code hooks}/{@code apps}) hold.
 */
class OpenAiPluginShapeTest {

  /** The schema's ten top-level keys (agent-plugins.org/schemas/1.0.0/plugin.schema.json). */
  private static final List<String> SCHEMA_TOP_LEVEL_KEYS =
      List.of(
          "$schema",
          "name",
          "version",
          "description",
          "author",
          "homepage",
          "repository",
          "license",
          "keywords",
          "extensions");

  /**
   * Copied from the schema's {@code name} pattern (agent-plugins.org/schemas/1.0.0/
   * plugin.schema.json), verified 2026-10-17: lower-case alphanumerics, {@code .} and {@code -},
   * never starting or ending with a separator and never carrying {@code --} or {@code ..}.
   */
  private static final Pattern NAME_PATTERN =
      Pattern.compile("^(?!.*(?:--|\\.\\.))[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$");

  private static final Pattern TOP_LEVEL_KEY =
      Pattern.compile("^ {2}\"([^\"]+)\":", Pattern.MULTILINE);

  private static String committed() {
    return OpenAiPluginJsonRenderer.render(CatalogueIndex.OPENAI_DIRECTORY);
  }

  @Test
  void everyTopLevelKeyIsOneTheSchemaAllows() {
    assertThat(topLevelKeys(committed())).isSubsetOf(SCHEMA_TOP_LEVEL_KEYS);
  }

  @Test
  void theTwoRequiredKeysArePresent() {
    assertThat(topLevelKeys(committed())).contains("$schema", "name");
  }

  @Test
  void theNameMatchesTheSchemasPattern() {
    assertThat(NAME_PATTERN.matcher(CatalogueIndex.OPENAI_DIRECTORY.base().name()).matches())
        .as("name must match the schema's pattern")
        .isTrue();
  }

  @Test
  void theCommittedFileCarriesNoVersionKey() {
    assertThat(committed()).doesNotContain("\"version\"");
  }

  @Test
  void carriesNoHooksOrAppsKey() {
    assertThat(committed()).doesNotContain("\"hooks\"").doesNotContain("\"apps\"");
  }

  private static List<String> topLevelKeys(String json) {
    List<String> keys = new ArrayList<>();
    Matcher matcher = TOP_LEVEL_KEY.matcher(json);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }
}
