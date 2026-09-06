/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DependencyConfiguratorTest {

  private static final String VERSION = "0.2.0-SNAPSHOT";

  @Test
  void proxyModeAddsApiCoreProxyClarityDiagramsAndJunit5() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-api"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-core"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-proxy"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-clarity"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-diagrams"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-junit5"));
    assertThat(deps).noneMatch(d -> d.artifact().contains("agent"));
    assertThat(deps).noneMatch(d -> d.artifact().contains("spring"));
  }

  @Test
  void agentModeAddsAgentInsteadOfProxy() {
    var deps =
        DependencyConfigurator.resolve(
            "agent", "junit5", "test", false, false, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-agent"));
    assertThat(deps).noneMatch(d -> d.artifact().contains("proxy"));
  }

  @Test
  void springModeAddsSpringAndProxy() {
    var deps =
        DependencyConfigurator.resolve(
            "spring", "junit5", "test", false, false, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-spring"));
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-proxy"));
    assertThat(deps).noneMatch(d -> d.artifact().contains("agent"));
  }

  @Test
  void junit5BringsThePlatformEngineOnTheTestRuntimeClasspath() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, VERSION);

    // The plugin calls useJUnitPlatform(); without this the first `gradle test` in a fresh
    // project fails with "Cannot create Launcher without at least one TestEngine".
    assertThat(deps)
        .contains(
            new DependencyConfigurator.ResolvedDependency(
                "testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.11.4"));
  }

  @Test
  void theEngineVersionMatchesTheApiTheJunit5ModuleExposes() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, VERSION);

    // Engine and API must move together; a split pair is a NoSuchMethodError at run time.
    assertThat(deps)
        .filteredOn(d -> d.artifact().startsWith("org.junit.jupiter:"))
        .allMatch(d -> d.artifact().endsWith(":5.11.4"));
  }

  @Test
  void junit4TestFramework() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit4", "test", false, false, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-junit4"));
    assertThat(deps).noneMatch(d -> d.artifact().contains("junit5"));
    // JUnit 4 is not switched onto the platform, so an engine would be dead weight — and
    // narrativetrace-junit4 already brings junit:junit.
    assertThat(deps).noneMatch(d -> d.artifact().contains("junit-jupiter-engine"));
    assertThat(deps).noneMatch(d -> "testRuntimeOnly".equals(d.configuration()));
  }

  @Test
  void micrometerModuleAdded() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, true, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-micrometer"));
  }

  @Test
  void slf4jModuleAdded() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", true, false, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-slf4j"));
  }

  @Test
  void springWebImpliesServlet() {
    var deps =
        DependencyConfigurator.resolve(
            "spring", "junit5", "test", false, false, false, true, VERSION);

    assertThat(deps).anyMatch(d -> d.artifact().contains("spring-web"));
    assertThat(deps).anyMatch(d -> d.artifact().contains("servlet"));
  }

  @Test
  void springWebWithExplicitServletDoesNotDuplicate() {
    var deps =
        DependencyConfigurator.resolve(
            "spring", "junit5", "test", false, false, true, true, VERSION);

    long servletCount = deps.stream().filter(d -> d.artifact().contains("servlet")).count();
    assertThat(servletCount).isEqualTo(1);
  }

  @Test
  void productionScopeUsesImplementation() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "production", false, false, false, false, VERSION);

    assertThat(deps).contains(dep("implementation", "narrativetrace-api"));
    assertThat(deps).contains(dep("implementation", "narrativetrace-core"));
    assertThat(deps).contains(dep("implementation", "narrativetrace-proxy"));
    // test framework always testImplementation
    assertThat(deps).contains(dep("testImplementation", "narrativetrace-junit5"));
  }

  @Test
  void micronautModuleAdded() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, false, true, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-micronaut"));
  }

  @Test
  void micronautHttpImpliesMicronaut() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, false, false, true, VERSION);

    assertThat(deps).anyMatch(d -> d.artifact().contains("micronaut-http"));
    assertThat(deps).anyMatch(d -> d.artifact().contains("narrativetrace-micronaut"));
  }

  @Test
  void micronautHttpWithExplicitMicronautDoesNotDuplicate() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, false, true, true, VERSION);

    long micronautCount =
        deps.stream()
            .filter(d -> d.artifact().contains("narrativetrace-micronaut:" + VERSION))
            .count();
    assertThat(micronautCount).isEqualTo(1);
  }

  @Test
  void opentelemetryModuleAdded() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, true, false, false, VERSION);

    assertThat(deps).contains(dep("testImplementation", "narrativetrace-opentelemetry"));
  }

  private DependencyConfigurator.ResolvedDependency dep(String config, String artifact) {
    return new DependencyConfigurator.ResolvedDependency(
        config, "ai.narrativetrace:" + artifact + ":" + VERSION);
  }
}
