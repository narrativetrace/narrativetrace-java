/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Adversarial probes for the Phase 6 M2 floor dependencies: {@link
 * DependencyConfigurator.ResolvedDependency#module()}, the resolve matrix, and the wiring that
 * {@code NarrativeTracePlugin.configureDependencies} performs on a real project.
 */
class AdversarialPhase6M2Test {

  private static final String VERSION = "0.2.0-SNAPSHOT";
  private static final String ENGINE = "org.junit.jupiter:junit-jupiter-engine";
  private static final String LAUNCHER = "org.junit.platform:junit-platform-launcher";

  /**
   * SUSPECTED DEFECT: {@code module()} strips from the LAST colon, so a Gradle coordinate with a
   * classifier ({@code group:name:version:classifier}) keeps its version in the "module". A floor
   * declared as {@code group:name:version} would then be added as {@code group:name:version}
   * without the constraint being matched to it.
   */
  @Test
  void aClassifiedCoordinateDeclaresOnlyGroupAndNameAsItsModule() {
    var dep =
        new DependencyConfigurator.ResolvedDependency("testRuntimeOnly", "g:a:1.0:tests", true);

    assertThat(dep.module()).isEqualTo("g:a");
  }

  /**
   * SUSPECTED DEFECT: a coordinate with no version has no version to strip; {@code module()} takes
   * {@code lastIndexOf(':')} and returns the group alone ({@code g}), not the coordinate itself.
   */
  @Test
  void aVersionlessCoordinateIsItsOwnModule() {
    var dep = new DependencyConfigurator.ResolvedDependency("testRuntimeOnly", "g:a", true);

    assertThat(dep.module()).isEqualTo("g:a");
  }

  @Test
  void theTwoJunitFloorsDeclareTheirVersionlessModules() {
    var deps =
        DependencyConfigurator.resolve(
            "proxy", "junit5", "test", false, false, false, false, VERSION);

    assertThat(deps.stream().filter(DependencyConfigurator.ResolvedDependency::floor))
        .extracting(DependencyConfigurator.ResolvedDependency::module)
        .containsExactlyInAnyOrder(ENGINE, LAUNCHER);
  }

  @Test
  void junit4ResolvesNoFloorInAnyScopeOrMode() {
    for (String scope : List.of("test", "production")) {
      for (String mode : List.of("proxy", "agent", "spring")) {
        var deps =
            DependencyConfigurator.resolve(mode, "junit4", scope, true, true, true, true, VERSION);
        assertThat(deps)
            .as(mode + "/" + scope)
            .noneMatch(DependencyConfigurator.ResolvedDependency::floor);
      }
    }
  }

  @Test
  void
      productionScopePutsEveryNarrativeTraceDependencyOnImplementationAndKeepsTheJunitFloorsOnTest() {
    var deps =
        DependencyConfigurator.resolve(
            "spring", "junit5", "production", true, true, true, true, VERSION);

    assertThat(deps)
        .filteredOn(d -> d.artifact().startsWith("ai.narrativetrace:"))
        .allMatch(
            d ->
                "implementation".equals(d.configuration())
                    || "testImplementation".equals(d.configuration()))
        .filteredOn(d -> !d.artifact().contains("junit"))
        .allMatch(d -> "implementation".equals(d.configuration()));
    assertThat(deps)
        .filteredOn(d -> d.artifact().startsWith("org.junit."))
        .allMatch(d -> "testRuntimeOnly".equals(d.configuration()));
  }

  /**
   * SUSPECTED DEFECT: the configurator maps every scope other than {@code "production"} to test
   * configurations without checking it. The plugin validates the scope first, but the configurator
   * is the point where a misspelled scope would silently move a production library onto the test
   * classpath, so it should refuse a value it does not know.
   */
  @Test
  void aScopeTheConfiguratorDoesNotKnowIsRefusedRatherThanTreatedAsTest() {
    assertThatThrownBy(
            () ->
                DependencyConfigurator.resolve(
                    "proxy", "junit5", "Production", false, false, false, false, VERSION))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * Every flag combination, every mode and both frameworks: no artifact is declared twice in one
   * configuration, and every NarrativeTrace coordinate carries exactly the version it was given.
   */
  @Test
  void noFlagCombinationDeclaresAnArtifactTwiceAndEveryNarrativeTraceCoordinateTakesTheVersion() {
    for (int bits = 0; bits < 128; bits++) {
      for (String mode : List.of("proxy", "agent", "spring")) {
        for (String framework : List.of("junit5", "junit4")) {
          var deps = resolveWith(bits, mode, framework);
          Set<String> seen = new HashSet<>();
          for (var dep : deps) {
            String key = dep.configuration() + " " + dep.artifact();
            assertThat(seen.add(key))
                .as("duplicate " + key + " for bits " + bits + " " + mode)
                .isTrue();
            if (dep.artifact().startsWith("ai.narrativetrace:")) {
              assertThat(dep.artifact()).endsWith(":" + VERSION);
            }
          }
        }
      }
    }
  }

  private static List<DependencyConfigurator.ResolvedDependency> resolveWith(
      int bits, String mode, String framework) {
    return DependencyConfigurator.resolve(
        mode,
        framework,
        "test",
        (bits & 1) != 0,
        (bits & 2) != 0,
        (bits & 4) != 0,
        (bits & 8) != 0,
        (bits & 16) != 0,
        (bits & 32) != 0,
        (bits & 64) != 0,
        VERSION);
  }

  /**
   * End to end through the real plugin: the JUnit floor reaches the project as a VERSIONLESS
   * dependency plus a version constraint on testRuntimeOnly, so a Spring Boot BOM's managed version
   * is not overridden by a direct pin.
   */
  @Test
  void theJunitFloorReachesARealProjectAsAVersionlessDependencyPlusAConstraint() {
    Project project = evaluatedProject("test");

    Configuration runtime = project.getConfigurations().getByName("testRuntimeOnly");
    List<String> declared = new ArrayList<>();
    for (Dependency dep : runtime.getDependencies()) {
      declared.add(dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion());
    }
    assertThat(declared).contains("org.junit.jupiter:junit-jupiter-engine:null");
    assertThat(declared).contains("org.junit.platform:junit-platform-launcher:null");
    assertThat(runtime.getDependencyConstraints())
        .extracting(c -> c.getGroup() + ":" + c.getName() + ":" + c.getVersion())
        .contains(ENGINE + ":5.11.4", LAUNCHER + ":1.11.4");
  }

  /** End to end: a production scope reaches the project's implementation configuration. */
  @Test
  void aProductionScopeReachesARealProjectOnImplementation() {
    Project project = evaluatedProject("production");

    Configuration implementation = project.getConfigurations().getByName("implementation");
    assertThat(implementation.getDependencies())
        .extracting(Dependency::getName)
        .contains("narrativetrace-api", "narrativetrace-core");
    assertThat(project.getConfigurations().getByName("testImplementation").getDependencies())
        .extracting(Dependency::getName)
        .doesNotContain("narrativetrace-api", "narrativetrace-core");
  }

  private static Project evaluatedProject(String scope) {
    Project project = ProjectBuilder.builder().build();
    project.getPluginManager().apply("java");
    project.getPluginManager().apply(NarrativeTracePlugin.class);
    var extension = (NarrativeTraceExtension) project.getExtensions().getByName("narrativeTrace");
    extension.getScope().set(scope);
    ((ProjectInternal) project).evaluate();
    return project;
  }
}
