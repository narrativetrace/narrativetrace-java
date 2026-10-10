/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.frameworks;

import java.util.List;

/**
 * The integration module(s) a row adds, and how a project can already be referencing them.
 *
 * <p>{@code coordinates} are what the row's dependency lines add: {@code group:artifact} for a
 * NarrativeTrace module (the version is the project's own NarrativeTrace version, filled in when
 * the line is rendered) or a full {@code group:artifact:version} for a third-party one. {@code
 * referencedBy} lists every NarrativeTrace {@code group:artifact} whose presence counts as
 * "referenced" — the row's own module plus any module that brings it transitively ({@code
 * narrativetrace-spring-web} brings {@code narrativetrace-spring}).
 *
 * @param coordinates what the row's dependency lines add, in order
 * @param configuration the Gradle configuration those lines use ({@code implementation}, {@code
 *     testImplementation}, {@code runtimeOnly}), or {@code null} for a jar no Gradle configuration
 *     takes — the agent's, which goes on the JVM command line
 * @param referencedBy coordinates that count as the module being referenced
 * @param plugin the Gradle plugin DSL setting that adds the module, or {@code null} when none does
 */
public record IntegrationModule(
    List<String> coordinates, String configuration, List<String> referencedBy, PluginDsl plugin) {

  private static final String NARRATIVETRACE_GROUP = "ai.narrativetrace:";
  private static final String IMPLEMENTATION = "implementation";

  public IntegrationModule {
    coordinates = List.copyOf(coordinates);
    referencedBy = List.copyOf(referencedBy);
    if (coordinates.isEmpty()) {
      throw new IllegalArgumentException("a row adds at least one module");
    }
    if (configuration != null && configuration.isBlank()) {
      throw new IllegalArgumentException("a module is added to a named Gradle configuration");
    }
    for (String coordinate : coordinates) {
      if (!coordinate.startsWith(NARRATIVETRACE_GROUP) && coordinate.split(":").length < 3) {
        throw new IllegalArgumentException(
            "a third-party coordinate carries its own version, got " + coordinate);
      }
    }
  }

  /**
   * Whether a manifest already references the module: one of {@link #referencedBy()} as a
   * dependency, or the plugin setting that adds it.
   */
  public boolean referencedIn(String manifestText) {
    boolean byCoordinate =
        referencedBy.stream()
            .map(ManifestPatterns::dependency)
            .anyMatch(p -> p.matcher(manifestText).find());
    return byCoordinate || (plugin != null && plugin.pattern().matcher(manifestText).find());
  }

  /**
   * The dependency lines a project without the plugin adds, one per coordinate: a NarrativeTrace
   * coordinate takes {@code narrativeTraceVersion}, a third-party one carries its own. A module no
   * Gradle configuration takes renders as the bare coordinate.
   */
  public List<String> dependencyLines(String narrativeTraceVersion) {
    return coordinates.stream()
        .map(c -> c.split(":").length == 2 ? c + ":" + narrativeTraceVersion : c)
        .map(c -> configuration == null ? c : configuration + "(\"" + c + "\")")
        .toList();
  }

  /**
   * The plugin line that adds the module where its plain lines put it, or {@code null} when no
   * plugin setting adds it: a module compiled against from {@code src/main} ({@code
   * implementation}) asks for the plugin's production scope too, since its default scope is test.
   */
  public String pluginLine() {
    return plugin == null ? null : plugin.line(IMPLEMENTATION.equals(configuration));
  }

  /**
   * How to add the module, both ways, as one sentence fragment: the plugin setting (plus any
   * third-party line the plugin does not add) and the plain dependency lines — or only the plain
   * lines when no plugin setting adds the module.
   */
  public String addInstruction(String narrativeTraceVersion) {
    List<String> plain = dependencyLines(narrativeTraceVersion);
    if (plugin == null) {
      return String.join(" and ", plain);
    }
    StringBuilder withPlugin = new StringBuilder(pluginLine());
    plain.stream()
        .filter(line -> !line.contains(NARRATIVETRACE_GROUP))
        .forEach(line -> withPlugin.append(" plus ").append(line));
    return "with the ai.narrativetrace Gradle plugin, "
        + withPlugin
        + "; without it, "
        + String.join(" and ", plain);
  }

  /** The row's first coordinate's artifact — the module a message names. */
  public String artifact() {
    String first = coordinates.get(0);
    return first.split(":")[1];
  }
}
