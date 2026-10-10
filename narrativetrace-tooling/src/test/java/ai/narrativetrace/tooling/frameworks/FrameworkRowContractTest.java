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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The guard clauses that keep a malformed row from ever reaching the doctor or the docs. */
class FrameworkRowContractTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"Spring", "spring_web", "-spring", "spring-", "spring--web", "spring.web"})
  void aRowIdIsKebabCase(String id) {
    assertThatThrownBy(() -> row(id, check("config.x-y")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"config.spring", "trap.spring-enabled", "config.Spring-enabled", "config."})
  void aWiringCheckIdIsConfigFrameworkThing(String id) {
    assertThatThrownBy(() -> new CheckBinding.WiringCheck(id))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWiringCheckNeedsWiringTheDoctorCanSee() {
    assertThatThrownBy(
            () ->
                new FrameworkRow(
                    "x",
                    "X",
                    marker(),
                    module(),
                    new Wiring.DependenciesOnly("deps"),
                    check("config.x-y"),
                    FrameworkRow.NO_TIER_B_CASE))
        .hasMessageContaining("source");
  }

  @Test
  void everyColumnIsRequired() {
    assertThatThrownBy(
            () -> new FrameworkRow("x", "X", null, module(), wiring(), check("config.x-y"), "none"))
        .hasMessageContaining("column");
    assertThatThrownBy(() -> new FrameworkRow("x", " ", marker(), module(), wiring(), null, "none"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new FrameworkRow("x", "X", marker(), module(), wiring(), check("config.x-y"), ""))
        .hasMessageContaining("Tier B");
  }

  @Test
  void aSourceWiringNamesItsFixtureAndItsEvidence() {
    assertThatThrownBy(() -> new Wiring.Snippet("d", " ", null, "java", List.of(evidence())))
        .hasMessageContaining("fixture");
    assertThatThrownBy(() -> new Wiring.Snippet("d", "f.java", null, "java", List.of()))
        .hasMessageContaining("evidence");
  }

  @Test
  void aModuleAddsAtLeastOneCoordinateToANamedConfiguration() {
    assertThatThrownBy(() -> new IntegrationModule(List.of(), "implementation", List.of(), null))
        .hasMessageContaining("at least one");
    assertThatThrownBy(() -> new IntegrationModule(List.of("a:b"), " ", List.of(), null))
        .hasMessageContaining("configuration");
  }

  @Test
  void aMarkerIsDescribed() {
    assertThatThrownBy(() -> new Marker(" ", List.of(), List.of()))
        .hasMessageContaining("description");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"spring web", "1x", "x-y"})
  void aPluginPropertyIsOneIdentifier(String property) {
    assertThatThrownBy(() -> new PluginDsl(property, "true", true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPluginPropertyHasAValue() {
    assertThatThrownBy(() -> new PluginDsl("slf4j", " ", true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPluginSettingRendersTheLineItRecognises() {
    PluginDsl modules = new PluginDsl("springWeb", "true", true);
    PluginDsl topLevel = new PluginDsl("mode", "\"spring\"", false);
    assertThat(modules.line()).isEqualTo("narrativeTrace { modules { springWeb.set(true) } }");
    assertThat(topLevel.line()).isEqualTo("narrativeTrace { mode.set(\"spring\") }");
    assertThat(modules.pattern().matcher(modules.line()).find()).isTrue();
    assertThat(topLevel.pattern().matcher(topLevel.line()).find()).isTrue();
    assertThat(modules.pattern().matcher("springWeb.set(trueish)").find()).isFalse();
    assertThat(modules.pattern().matcher("springWebX.set(true)").find()).isFalse();
  }

  /**
   * The plugin's default scope is test: a {@code mode.set("spring")} alone lands
   * narrativetrace-spring on testImplementation, and the @EnableNarrativeTrace the same fix adds
   * under src/main cannot compile. Found building the Spring Boot Tier B case's solved tree.
   */
  @Test
  void aCompileTimeRowsPluginLineAsksForProductionScope() {
    assertThat(FrameworkTable.row("spring").orElseThrow().module().pluginLine())
        .isEqualTo("narrativeTrace { scope.set(\"production\"); mode.set(\"spring\") }");
    assertThat(FrameworkTable.row("spring-web").orElseThrow().module().pluginLine())
        .isEqualTo("narrativeTrace { scope.set(\"production\"); modules { springWeb.set(true) } }");
  }

  @Test
  void aTestOrRuntimeRowsPluginLineLeavesTheScopeAlone() {
    for (String id : List.of("junit4", "default-logger")) {
      IntegrationModule module = FrameworkTable.row(id).orElseThrow().module();
      assertThat(module.pluginLine()).as(id).isEqualTo(module.plugin().line());
    }
  }

  @Test
  void aModuleNoPluginSettingAddsHasNoPluginLine() {
    IntegrationModule noPlugin =
        new IntegrationModule(
            List.of("ai.narrativetrace:narrativetrace-x"),
            "implementation",
            List.of("ai.narrativetrace:narrativetrace-x"),
            null);
    assertThat(noPlugin.pluginLine()).isNull();
  }

  @Test
  void dependencyLinesTakeTheProjectsVersionOnlyForNarrativeTraceCoordinates() {
    IntegrationModule logger = FrameworkTable.row("default-logger").orElseThrow().module();
    assertThat(logger.dependencyLines("1.2.3"))
        .containsExactly(
            "runtimeOnly(\"ai.narrativetrace:narrativetrace-slf4j:1.2.3\")",
            "runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")");
  }

  private static FrameworkRow row(String id, CheckBinding check) {
    return new FrameworkRow(id, "X", marker(), module(), wiring(), check, "none");
  }

  private static CheckBinding check(String id) {
    return new CheckBinding.WiringCheck(id);
  }

  private static Marker marker() {
    return new Marker("m", List.of(), List.of());
  }

  private static IntegrationModule module() {
    return new IntegrationModule(
        List.of("ai.narrativetrace:narrativetrace-x"),
        "implementation",
        List.of("ai.narrativetrace:narrativetrace-x"),
        null);
  }

  private static Wiring wiring() {
    return new Wiring.Snippet("d", "f.java", null, "java", List.of(evidence()));
  }

  private static Evidence evidence() {
    return Evidence.matching("x");
  }
}
