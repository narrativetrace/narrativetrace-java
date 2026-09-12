/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.ArrayList;
import java.util.List;

final class DependencyConfigurator {

  private static final String GROUP = "ai.narrativetrace";

  /**
   * The JUnit Platform engine the plugin puts on the test runtime classpath for {@code
   * testFramework = "junit5"}.
   *
   * <p>The plugin calls {@code useJUnitPlatform()}, and the platform refuses to start without an
   * engine. {@code narrativetrace-junit5} exposes {@code junit-jupiter-api} (compile-time), so the
   * test *compiled* and then failed at run time with "Cannot create Launcher without at least one
   * TestEngine" — a message that names neither NarrativeTrace nor the artifact to add. Supplying
   * the engine is what makes the documented one-line setup true on first contact.
   *
   * <p>Pinned to the version this repository builds and tests against. A project that wants a
   * different one declares it itself; Gradle's conflict resolution then picks the higher version,
   * so this is a floor rather than a constraint.
   */
  private static final String JUNIT_JUPITER_ENGINE =
      "org.junit.jupiter:junit-jupiter-engine:5.11.4";

  /**
   * The JUnit Platform launcher the plugin puts on the test runtime classpath alongside {@link
   * #JUNIT_JUPITER_ENGINE}, for {@code testFramework = "junit5"}.
   *
   * <p>Gradle 8 supplies a version of this itself when only an engine is declared (a deprecated
   * behaviour it warns about on every run); Gradle 9 removes that auto-management outright, and
   * {@code useJUnitPlatform()} then fails the test *process* before any engine, extension, or test
   * class ever runs — confirmed against a real Gradle 9.0.0 run: "Could not start Gradle Test
   * Executor 1: Failed to load JUnit Platform." Declaring the launcher ourselves makes the plugin's
   * one-line setup Gradle-9-proof rather than riding on a warned-about default.
   *
   * <p>Pinned to the release this build already resolves the launcher to (see the root {@code
   * build.gradle.kts} comment above {@code pitestJunitBom}), the same lockstep the engine version
   * follows.
   */
  private static final String JUNIT_PLATFORM_LAUNCHER =
      "org.junit.platform:junit-platform-launcher:1.11.4";

  private DependencyConfigurator() {}

  record ResolvedDependency(String configuration, String artifact) {}

  static List<ResolvedDependency> resolve(
      String mode,
      String testFramework,
      String scope,
      boolean slf4j,
      boolean micrometer,
      boolean servlet,
      boolean springWeb,
      String version) {
    return resolve(
        mode,
        testFramework,
        scope,
        slf4j,
        micrometer,
        servlet,
        springWeb,
        false,
        false,
        false,
        version);
  }

  static List<ResolvedDependency> resolve(
      String mode,
      String testFramework,
      String scope,
      boolean slf4j,
      boolean micrometer,
      boolean servlet,
      boolean springWeb,
      boolean opentelemetry,
      boolean micronaut,
      boolean micronautHttp,
      String version) {

    var libConfig = "production".equals(scope) ? "implementation" : "testImplementation";
    var deps = new ArrayList<ResolvedDependency>();

    addCoreDeps(deps, libConfig, version);
    addModeDeps(deps, mode, libConfig, version);
    addTestFrameworkDep(deps, testFramework, version);
    addModuleDeps(
        deps,
        libConfig,
        version,
        slf4j,
        micrometer,
        servlet,
        springWeb,
        opentelemetry,
        micronaut,
        micronautHttp);

    return List.copyOf(deps);
  }

  private static void addCoreDeps(List<ResolvedDependency> deps, String config, String version) {
    // api first: it is what annotations and the event model come from, and core exposes it
    // transitively anyway — declaring it makes the compile-time contract visible in the build file
    // rather than implicit in another module's POM.
    deps.add(new ResolvedDependency(config, gav("narrativetrace-api", version)));
    deps.add(new ResolvedDependency(config, gav("narrativetrace-core", version)));
    deps.add(new ResolvedDependency(config, gav("narrativetrace-clarity", version)));
    deps.add(new ResolvedDependency(config, gav("narrativetrace-diagrams", version)));
  }

  private static void addModeDeps(
      List<ResolvedDependency> deps, String mode, String config, String version) {
    switch (mode) {
      case "proxy" ->
          deps.add(new ResolvedDependency(config, gav("narrativetrace-proxy", version)));
      case "agent" ->
          deps.add(new ResolvedDependency(config, gav("narrativetrace-agent", version)));
      case "spring" -> {
        deps.add(new ResolvedDependency(config, gav("narrativetrace-spring", version)));
        deps.add(new ResolvedDependency(config, gav("narrativetrace-proxy", version)));
      }
      default -> {
        /* validated elsewhere */
      }
    }
  }

  /**
   * The test-framework module, plus — for JUnit 5 only — the platform engine and launcher that
   * {@code useJUnitPlatform()} needs at run time. JUnit 4 gets neither: the plugin does not switch
   * it onto the platform, and {@code narrativetrace-junit4} already brings {@code junit:junit}.
   */
  private static void addTestFrameworkDep(
      List<ResolvedDependency> deps, String testFramework, String version) {
    if ("junit4".equals(testFramework)) {
      deps.add(new ResolvedDependency("testImplementation", gav("narrativetrace-junit4", version)));
      return;
    }
    deps.add(new ResolvedDependency("testImplementation", gav("narrativetrace-junit5", version)));
    deps.add(new ResolvedDependency("testRuntimeOnly", JUNIT_JUPITER_ENGINE));
    deps.add(new ResolvedDependency("testRuntimeOnly", JUNIT_PLATFORM_LAUNCHER));
  }

  private static void addModuleDeps(
      List<ResolvedDependency> deps,
      String config,
      String version,
      boolean slf4j,
      boolean micrometer,
      boolean servlet,
      boolean springWeb,
      boolean opentelemetry,
      boolean micronaut,
      boolean micronautHttp) {
    if (slf4j) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-slf4j", version)));
    }
    if (micrometer) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-micrometer", version)));
    }
    if (springWeb) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-spring-web", version)));
      if (!servlet) {
        deps.add(new ResolvedDependency(config, gav("narrativetrace-servlet", version)));
      }
    }
    if (servlet) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-servlet", version)));
    }
    if (opentelemetry) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-opentelemetry", version)));
    }
    if (micronautHttp) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-micronaut-http", version)));
      if (!micronaut) {
        deps.add(new ResolvedDependency(config, gav("narrativetrace-micronaut", version)));
      }
    }
    if (micronaut) {
      deps.add(new ResolvedDependency(config, gav("narrativetrace-micronaut", version)));
    }
  }

  private static String gav(String artifact, String version) {
    return GROUP + ":" + artifact + ":" + version;
  }
}
