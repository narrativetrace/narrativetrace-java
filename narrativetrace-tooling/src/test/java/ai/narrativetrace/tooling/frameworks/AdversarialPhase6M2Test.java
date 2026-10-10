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
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Adversarial probes for the Phase 6 M2 changes: {@link PluginDsl#line(boolean)}, {@link
 * IntegrationModule#pluginLine()} and {@link IntegrationModule#addInstruction(String)}.
 */
class AdversarialPhase6M2Test {

  private static final String VERSION = "9.9.9";
  private static final String PRODUCTION = "scope.set(\"production\")";

  @Test
  void everyRowsPluginLineRecognisesItsOwnSetting() {
    int withPlugin = 0;
    for (FrameworkRow row : FrameworkTable.ROWS) {
      IntegrationModule module = row.module();
      if (module.plugin() == null) {
        continue;
      }
      withPlugin++;
      assertThat(module.pluginLine()).as(row.id()).startsWith("narrativeTrace { ").endsWith(" }");
      assertThat(module.plugin().pattern().matcher(module.pluginLine()).find())
          .as(row.id())
          .isTrue();
    }
    assertThat(withPlugin).isPositive();
  }

  @Test
  void theProductionScopeIsAskedForExactlyWhenTheModuleIsCompiledFromSrcMain() {
    for (FrameworkRow row : FrameworkTable.ROWS) {
      IntegrationModule module = row.module();
      if (module.plugin() == null) {
        continue;
      }
      boolean implementation = "implementation".equals(module.configuration());
      assertThat(module.pluginLine().contains(PRODUCTION)).as(row.id()).isEqualTo(implementation);
    }
  }

  /**
   * Inverted from the adversarial draft, which expected production scope for every non-test row.
   * The one runtimeOnly row, the default logger, is bound to trap.silent-sink — a TEST-path finding
   * (the JUnit extension registered, file output off, no consumer) — so the plugin's default test
   * scope puts the logger exactly where that finding needs it; production scope would move every
   * NarrativeTrace library into the shipped artifact to fix a test-time problem. Only rows compiled
   * against from src/main ask for production.
   */
  @Test
  void onlyACompileTimeRowAsksForTheProductionScope() {
    for (FrameworkRow row : FrameworkTable.ROWS) {
      IntegrationModule module = row.module();
      if (module.plugin() == null) {
        continue;
      }
      boolean compileTime = "implementation".equals(module.configuration());
      assertThat(module.pluginLine().contains(PRODUCTION)).as(row.id()).isEqualTo(compileTime);
    }
  }

  @Test
  void aTestConfigurationModuleKeepsThePluginsDefaultScope() {
    IntegrationModule module =
        new IntegrationModule(
            List.of("ai.narrativetrace:narrativetrace-x"),
            "testImplementation",
            List.of("ai.narrativetrace:narrativetrace-x"),
            new PluginDsl("x", "true", true));

    assertThat(module.pluginLine()).isEqualTo("narrativeTrace { modules { x.set(true) } }");
  }

  @Test
  void theProductionScopeIsWrittenBeforeTheSettingInBothShapes() {
    assertThat(new PluginDsl("mode", "\"spring\"", false).line(true))
        .isEqualTo("narrativeTrace { scope.set(\"production\"); mode.set(\"spring\") }");
    assertThat(new PluginDsl("springWeb", "true", true).line(true))
        .isEqualTo("narrativeTrace { scope.set(\"production\"); modules { springWeb.set(true) } }");
  }

  @Test
  void lineFalseIsExactlyTheDefaultLine() {
    PluginDsl dsl = new PluginDsl("springWeb", "true", true);

    assertThat(dsl.line(false)).isEqualTo(dsl.line());
  }

  @Test
  void aProductionScopeLineStillRecognisesItsSettingAndNotTheScope() {
    PluginDsl topLevel = new PluginDsl("mode", "\"spring\"", false);
    PluginDsl inModules = new PluginDsl("springWeb", "true", true);

    assertThat(topLevel.pattern().matcher(topLevel.line(true)).find()).isTrue();
    assertThat(inModules.pattern().matcher(inModules.line(true)).find()).isTrue();
    assertThat(topLevel.pattern().matcher(PRODUCTION).find()).isFalse();
  }

  @Test
  void theSettingPatternAcceptsGroovyAndSingleQuotedForms() {
    PluginDsl mode = new PluginDsl("mode", "\"spring\"", false);

    assertThat(mode.pattern().matcher("narrativeTrace { mode = \"spring\" }").find()).isTrue();
    assertThat(mode.pattern().matcher("narrativeTrace { mode = 'spring' }").find()).isTrue();
    assertThat(mode.pattern().matcher("narrativeTrace { mode.set('spring') }").find()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "mode.set(\"springs\")",
        "mode.set(\"spring-boot\")",
        "modeX.set(\"spring\")",
        "xmode.set(\"spring\")",
        "mode.set(spring)",
        "mode.set(\"spring\"x"
      })
  void theSettingPatternRejectsNearMissValues(String text) {
    PluginDsl mode = new PluginDsl("mode", "\"spring\"", false);

    assertThat(mode.pattern().matcher(text).find()).as(text).isFalse();
  }

  @Test
  void theBooleanSettingPatternRejectsFalseAndNearMisses() {
    PluginDsl flag = new PluginDsl("springWeb", "true", true);

    assertThat(flag.pattern().matcher("springWeb = false").find()).isFalse();
    assertThat(flag.pattern().matcher("springWeb.set(false)").find()).isFalse();
    assertThat(flag.pattern().matcher("springWeb.set(true-ish)").find()).isFalse();
  }

  @Test
  void addInstructionWithoutAPluginIsOnlyTheJoinedPlainLines() {
    IntegrationModule module =
        new IntegrationModule(
            List.of("a.b:c:1.0", "ai.narrativetrace:narrativetrace-x"),
            "implementation",
            List.of("ai.narrativetrace:narrativetrace-x"),
            null);

    assertThat(module.addInstruction(VERSION))
        .isEqualTo(
            "implementation(\"a.b:c:1.0\") and implementation(\"ai.narrativetrace:narrativetrace-x:"
                + VERSION
                + "\")");
  }

  @Test
  void addInstructionForTheLoggerRowAddsOnlyTheThirdPartyLineToThePluginWording() {
    IntegrationModule logger = FrameworkTable.row("default-logger").orElseThrow().module();

    String instruction = logger.addInstruction(VERSION);

    assertThat(instruction)
        .startsWith("with the ai.narrativetrace Gradle plugin, narrativeTrace { ")
        .contains("slf4j.set(true) }")
        .contains(" plus runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\"); without it, ")
        .endsWith(
            "runtimeOnly(\"ai.narrativetrace:narrativetrace-slf4j:"
                + VERSION
                + "\") and runtimeOnly(\"ch.qos.logback:logback-classic:1.5.38\")");
    assertThat(instruction.substring(0, instruction.indexOf("; without it")))
        .doesNotContain("plus runtimeOnly(\"ai.narrativetrace");
  }

  @Test
  void theAgentRowAddsOnlyThePluginSettingWithNoPlainLine() {
    IntegrationModule agent = FrameworkTable.row("agent").orElseThrow().module();

    assertThat(agent.addInstruction(VERSION))
        .isEqualTo(
            "with the ai.narrativetrace Gradle plugin, narrativeTrace { mode.set(\"agent\") }; "
                + "without it, ai.narrativetrace:narrativetrace-agent:"
                + VERSION);
  }

  @Test
  void theAgentRowsPlainLineIsTheJarNotAGradleConfiguration() {
    IntegrationModule agent = FrameworkTable.row("agent").orElseThrow().module();

    assertThat(agent.dependencyLines(VERSION))
        .containsExactly("ai.narrativetrace:narrativetrace-agent:" + VERSION);
  }

  /**
   * SUSPECTED DEFECT: {@code dependencyLines} decides whether to append the NarrativeTrace version
   * by segment count, not by group. A third-party coordinate without a version is malformed (the
   * record's own Javadoc says a third-party one "carries its own" version), and must be refused
   * rather than silently gaining the NarrativeTrace version.
   */
  @Test
  void aThirdPartyCoordinateWithoutItsOwnVersionIsRefused() {
    assertThatThrownBy(
            () ->
                new IntegrationModule(
                    List.of("ch.qos.logback:logback-classic"), "runtimeOnly", List.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theArtifactOfARowIsTheSecondSegmentOfItsFirstCoordinate() {
    assertThat(FrameworkTable.row("default-logger").orElseThrow().module().artifact())
        .isEqualTo("narrativetrace-slf4j");
    assertThat(
            new IntegrationModule(
                    List.of("ch.qos.logback:logback-classic:1.5.38"),
                    "runtimeOnly",
                    List.of(),
                    null)
                .artifact())
        .isEqualTo("logback-classic");
  }
}
