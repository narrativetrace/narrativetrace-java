/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.frameworks.FrameworkRow;
import ai.narrativetrace.tooling.frameworks.FrameworkTable;
import ai.narrativetrace.tooling.frameworks.PluginDsl;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The doctor's framework table tells a plugin user which {@code narrativeTrace { … } } line adds a
 * row's module; this holds that line to what the plugin really does. A table row naming a property
 * the plugin ignores, or one that adds a different module, fails here rather than in a project.
 */
class FrameworkTablePluginDslTest {

  private static final String VERSION = "9.9.9";

  static List<FrameworkRow> rowsWithAPluginSetting() {
    return FrameworkTable.ROWS.stream().filter(row -> row.module().plugin() != null).toList();
  }

  @ParameterizedTest
  @MethodSource("rowsWithAPluginSetting")
  void thePluginSettingAddsTheRowsModule(FrameworkRow row) {
    PluginDsl dsl = row.module().plugin();
    String expected = row.module().coordinates().get(0) + ":" + VERSION;

    assertThat(resolve(dsl))
        .extracting(DependencyConfigurator.ResolvedDependency::artifact)
        .as(row.id() + ": " + dsl.line())
        .contains(expected);
  }

  /**
   * The line the doctor prints, resolved in the scope that line asks for, lands a compile-time
   * row's module on {@code implementation} — where its wiring under {@code src/main} can compile
   * against it. The test above resolves in production scope regardless of the line, which is how a
   * fix without {@code scope.set("production")} once passed here and failed in a Spring Boot
   * project.
   */
  @ParameterizedTest
  @MethodSource("rowsWithAPluginSetting")
  void thePrintedLineLandsTheModuleWhereItsWiringCompiles(FrameworkRow row) {
    String line = row.module().pluginLine();
    String scope = line.contains("scope.set(\"production\")") ? "production" : "test";
    String expected = row.module().coordinates().get(0) + ":" + VERSION;
    String wanted =
        "implementation".equals(row.module().configuration())
            ? "implementation"
            : "testImplementation";

    assertThat(resolve(row.module().plugin(), scope))
        .filteredOn(dependency -> dependency.artifact().equals(expected))
        .extracting(DependencyConfigurator.ResolvedDependency::configuration)
        .as(row.id() + ": " + line)
        .containsOnly(wanted);
  }

  private static List<DependencyConfigurator.ResolvedDependency> resolve(PluginDsl dsl) {
    return resolve(dsl, "production");
  }

  private static List<DependencyConfigurator.ResolvedDependency> resolve(
      PluginDsl dsl, String scope) {
    String value = dsl.value().replace("\"", "");
    Set<String> on = "true".equals(value) ? Set.of(dsl.property()) : Set.of();
    String mode = "mode".equals(dsl.property()) ? value : "proxy";
    String testFramework = "testFramework".equals(dsl.property()) ? value : "junit5";
    return DependencyConfigurator.resolve(
        mode,
        testFramework,
        scope,
        on.contains("slf4j"),
        on.contains("micrometer"),
        on.contains("servlet"),
        on.contains("springWeb"),
        on.contains("opentelemetry"),
        on.contains("micronaut"),
        on.contains("micronautHttp"),
        VERSION);
  }
}
