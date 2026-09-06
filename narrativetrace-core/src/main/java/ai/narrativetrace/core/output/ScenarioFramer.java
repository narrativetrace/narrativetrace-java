/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

/**
 * Converts display names into human-readable scenario titles.
 *
 * <p>INTENT: Use this to derive stable, readable headings from JUnit display names and camel-case
 * method names without duplicating naming heuristics across renderers.
 */
public final class ScenarioFramer {

  private ScenarioFramer() {}

  public static String frame(String displayName) {
    return "Scenario: " + humanize(displayName);
  }

  public static String humanize(String displayName) {
    var name = displayName.replaceAll("\\([^)]*\\)$", "");
    if (name.isEmpty()) {
      return "";
    }
    if (name.contains(" ")) {
      return name;
    }
    var words = name.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }
}
