/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

class BuildConfigurationTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  private BuildResult gradle(String... args) {
    var allArgs = new ArrayList<>(List.of(args));
    allArgs.add("-q");
    return GradleRunner.create().withProjectDir(PROJECT_DIR).withArguments(allArgs).build();
  }

  /**
   * The release workflow's only source of truth for what it is releasing.
   *
   * <p>INTENT: `release.yml` used to `sed` a `version = "..."` literal out of `build.gradle.kts`
   * and out of the plugin's build file. Neither file has ever contained one — the version comes
   * from `gradle.properties` through `providers.gradleProperty("narrativetraceVersion")` — so both
   * greps matched nothing: the tag-vs-version assertion compared `"v"` against the tag and failed
   * every release, and the plugin check built a URL with an empty version and fell through to
   * publishing. Found by an adversarial review. A task the build itself answers cannot drift from
   * the build the way a regex over its source can.
   */
  @Test
  void printVersionReportsExactlyTheDeclaredProjectVersion() throws Exception {
    var declared = declaredVersion();

    var printed = gradle("printVersion").getOutput().strip();

    assertThat(printed).isEqualTo(declared).isNotEmpty();
  }

  @Test
  void printVersionEmitsOneLineAndNothingElse() {
    var printed = gradle("printVersion").getOutput().strip();

    assertThat(printed.lines()).hasSize(1);
    assertThat(printed).doesNotContain(" ");
  }

  /**
   * `scripts/verify-publication.sh`'s coordinate-derivation input — see the task's own doc comment
   * in {@code build.gradle.kts}. One {@code LIBRARY} line per {@code publishedModules} entry, group
   * and artifactId only (no version: {@code printVersion} owns that).
   */
  @Test
  void printPublishedCoordinatesListsEveryLibraryModule() {
    var printed = gradle("printPublishedCoordinates").getOutput();

    assertThat(printed)
        .contains("LIBRARY ai.narrativetrace narrativetrace-api")
        .contains("LIBRARY ai.narrativetrace narrativetrace-core")
        .contains("LIBRARY ai.narrativetrace narrativetrace-proxy")
        .contains("LIBRARY ai.narrativetrace narrativetrace-clarity")
        .contains("LIBRARY ai.narrativetrace narrativetrace-diagrams")
        .contains("LIBRARY ai.narrativetrace narrativetrace-glossary")
        .contains("LIBRARY ai.narrativetrace narrativetrace-slf4j")
        .contains("LIBRARY ai.narrativetrace narrativetrace-agent")
        .contains("LIBRARY ai.narrativetrace narrativetrace-spring")
        .contains("LIBRARY ai.narrativetrace narrativetrace-spring-web")
        .contains("LIBRARY ai.narrativetrace narrativetrace-servlet")
        .contains("LIBRARY ai.narrativetrace narrativetrace-micrometer")
        .contains("LIBRARY ai.narrativetrace narrativetrace-micronaut")
        .contains("LIBRARY ai.narrativetrace narrativetrace-micronaut-http")
        .contains("LIBRARY ai.narrativetrace narrativetrace-opentelemetry")
        .contains("LIBRARY ai.narrativetrace narrativetrace-junit4")
        .contains("LIBRARY ai.narrativetrace narrativetrace-junit5");
    assertThat(printed.lines().filter(line -> line.startsWith("LIBRARY ")).count()).isEqualTo(17);
  }

  @Test
  void printPublishedCoordinatesListsTheGradlePluginPortalMarker() {
    var printed = gradle("printPublishedCoordinates").getOutput();

    assertThat(printed).contains("PLUGIN ai.narrativetrace ai.narrativetrace.gradle.plugin");
    assertThat(printed.lines().filter(line -> line.startsWith("PLUGIN ")).count()).isEqualTo(1);
  }

  /**
   * A module the release deliberately does not ship (test infrastructure, examples, benchmarks) is
   * exactly as much a bug in this list as a missing one: the verification script would poll a
   * coordinate no release ever produces and report a false "missing".
   */
  @Test
  void printPublishedCoordinatesExcludesNonPublishedModules() {
    var printed = gradle("printPublishedCoordinates").getOutput();

    assertThat(printed)
        .doesNotContain("narrativetrace-benchmarks")
        .doesNotContain("narrativetrace-build-tests")
        .doesNotContain("narrativetrace-junit4-example")
        .doesNotContain("narrativetrace-agent-example")
        .doesNotContain("narrativetrace-jcstress")
        .doesNotContain("narrativetrace-security-tests")
        .doesNotContain("narrativetrace-examples")
        .doesNotContain("LIBRARY ai.narrativetrace narrativetrace-gradle-plugin");
  }

  /** Reads `narrativetraceVersion` out of gradle.properties without going through Gradle. */
  private String declaredVersion() throws Exception {
    var props = new java.util.Properties();
    try (var in = new java.io.FileInputStream(new File(PROJECT_DIR, "gradle.properties"))) {
      props.load(in);
    }
    return props.getProperty("narrativetraceVersion");
  }

  @Test
  void allExpectedModulesAreIncluded() {
    var result = gradle(":projects");
    var output = result.getOutput();

    assertThat(output)
        .contains("narrativetrace-api")
        .contains("narrativetrace-core")
        .contains("narrativetrace-proxy")
        .contains("narrativetrace-junit4")
        .contains("narrativetrace-junit4-example")
        .contains("narrativetrace-junit5")
        .contains("narrativetrace-examples")
        .contains("narrativetrace-clarity")
        .contains("narrativetrace-diagrams")
        .contains("narrativetrace-slf4j")
        .contains("narrativetrace-agent")
        .contains("narrativetrace-spring")
        .contains("narrativetrace-spring-web")
        .contains("narrativetrace-servlet")
        .contains("narrativetrace-opentelemetry")
        .contains("narrativetrace-benchmarks")
        .contains("narrativetrace-micrometer")
        .contains("narrativetrace-jcstress")
        .contains("narrativetrace-build-tests")
        .contains("narrativetrace-gradle-plugin")
        .contains("narrativetrace-micronaut")
        .contains("narrativetrace-micronaut-http");
  }

  /**
   * Every distributable agent jar must be attachable with {@code -javaagent}, so each one is
   * asserted rather than an arbitrary member of the directory: after a publishing run {@code
   * build/libs} also holds {@code -sources} and {@code -javadoc} jars, which carry no premain
   * attributes, and {@code listFiles} order is filesystem-dependent.
   */
  @Test
  void agentJarManifest() throws Exception {
    gradle(":narrativetrace-agent:jar");
    var jarDir = new File(PROJECT_DIR, "narrativetrace-agent/build/libs");
    var jarFiles =
        jarDir.listFiles(
            (dir, name) ->
                name.endsWith(".jar") && !name.contains("-sources") && !name.contains("-javadoc"));
    assertThat(jarFiles).isNotEmpty();

    for (var jarFile : jarFiles) {
      try (var jar = new java.util.jar.JarFile(jarFile)) {
        var attrs = jar.getManifest().getMainAttributes();
        assertThat(attrs.getValue("Premain-Class"))
            .as("%s declares the premain entry point", jarFile.getName())
            .isEqualTo("ai.narrativetrace.agent.NarrativeTraceAgent");
        assertThat(attrs.getValue("Can-Retransform-Classes"))
            .as("%s allows retransformation", jarFile.getName())
            .isEqualTo("true");
      }
    }
  }

  @Test
  void coreModulePublicationConfigured() {
    var result = gradle(":narrativetrace-core:tasks", "--all");
    assertThat(result.getOutput()).contains("publishMavenJavaPublicationToMavenLocal");
  }

  @Test
  void exampleCustomTasksRegistered() {
    var result = gradle(":narrativetrace-examples:tasks", "--all");
    var output = result.getOutput();

    assertThat(output)
        .contains("runExamples")
        .contains("traceExamples")
        .contains("ecommerce:run")
        .contains("minecraft:run")
        .contains("library:run")
        .contains("clarity:run")
        .contains("ecommerce:traceTests")
        .contains("ecommerce:traceMermaid")
        .contains("ecommerce:tracePlantUml")
        .contains("ecommerce:renderDiagrams");
  }

  @Test
  void verificationTasksExistAtRoot() {
    var result = gradle("tasks", "--group=verification");
    var output = result.getOutput();

    assertThat(output)
        .contains("metricsReport")
        .contains("coverageReport")
        .contains("pmdReport")
        .contains("pitest")
        .contains("pitestAgent")
        .contains("mutationReport")
        .contains("calibratePerf")
        .contains("dependencyReport")
        .contains("jdependReport")
        .contains("jdependCrossModule")
        .contains("demoWiringCheck");
  }

  /**
   * The agent module's pitest run is deliberately its own task, not folded into the shared {@code
   * pitest} aggregate — see the root build script comment above the {@code narrativetrace-agent}
   * pitest configuration for why (a blowout in ASM-instrumentation-heavy mutants must not consume
   * the time box the other five modules share).
   */
  @Test
  void pitestAgentTaskDependsOnTheAgentModuleOnly() {
    var result = gradle("pitestAgent", "--dry-run");

    assertThat(result.getOutput()).contains(":narrativetrace-agent:pitest");
  }

  /**
   * A bare {@code pitest} would not prove isolation: Gradle's CLI matches a task name across every
   * subproject, and the agent module now has its own same-named {@code pitest} task, so a bare
   * invocation runs it too. CI therefore calls the fully-qualified {@code :pitest} (see the private
   * CI's {@code mutation} job) — that is what this test must pin.
   */
  @Test
  void sharedPitestAggregateDoesNotPullInTheAgentModule() {
    var result = gradle(":pitest", "--dry-run");

    assertThat(result.getOutput()).doesNotContain(":narrativetrace-agent:pitest");
  }

  /**
   * Documents the pitfall {@code sharedPitestAggregateDoesNotPullInTheAgentModule} guards against:
   * a <em>bare</em> {@code pitest} is not scoped to the root project, so it still matches and runs
   * the agent module's same-named task. If this assertion ever fails, Gradle's name-matching
   * behavior changed, not this repo's wiring — the fully-qualified {@code :pitest} in the private
   * CI configuration is what actually keeps the two isolated, not this task's own name.
   */
  @Test
  void bareTaskNameMatchingIsWhyCiUsesTheFullyQualifiedPath() {
    var result = gradle("pitest", "--dry-run");

    assertThat(result.getOutput()).contains(":narrativetrace-agent:pitest");
  }

  @Test
  void pmdTasksExistOnProductionModules() {
    var result = gradle(":narrativetrace-core:tasks", "--all");
    var output = result.getOutput();

    assertThat(output).contains("pmdMain").contains("pmdMetrics");
  }

  @Test
  void spotlessCheckWiredIntoCheck() {
    var result = gradle(":narrativetrace-core:check", "--dry-run");
    assertThat(result.getOutput()).contains("spotlessCheck");
  }

  @Test
  void spotbugsMainWiredIntoCheck() {
    var result = gradle(":narrativetrace-core:check", "--dry-run");
    assertThat(result.getOutput()).contains("spotbugsMain");
  }

  /**
   * spotbugsTest is registered by the plugin but disabled: the fuzz/hostile-input fixtures in
   * narrativetrace-security-tests deliberately do the things a security linter exists to flag on
   * production code, and it cannot tell "this is the test subject" from "this is the
   * vulnerability." See documentation/security-tooling.md.
   */
  @Test
  void spotbugsTestDisabled() {
    var result = gradle(":narrativetrace-core:spotbugsTest", "--dry-run");
    assertThat(result.getOutput()).contains(":narrativetrace-core:spotbugsTest SKIPPED");
  }

  @Test
  void perfTestTaskRegisteredOnSubprojects() {
    var result = gradle(":narrativetrace-core:tasks", "--all");
    assertThat(result.getOutput()).contains("perfTest");
  }

  @Test
  void testFinalizedByJacocoReport() {
    var result = gradle(":narrativetrace-core:test", "--dry-run");
    assertThat(result.getOutput()).contains("jacocoTestReport");
  }

  @Test
  void jacocoVerificationNotWiredForBenchmarks() {
    var result = gradle(":narrativetrace-benchmarks:check", "--dry-run");
    assertThat(result.getOutput()).doesNotContain("jacocoTestCoverageVerification");
  }

  @Test
  void jacocoVerificationWiredIntoCheckForProductionModules() {
    var result = gradle(":narrativetrace-core:check", "--dry-run");
    assertThat(result.getOutput()).contains("jacocoTestCoverageVerification");
  }

  @Test
  void springDependsOnCoreProxy() {
    var result =
        gradle(":narrativetrace-spring:dependencies", "--configuration", "compileClasspath");
    var output = result.getOutput();

    assertThat(output).contains("narrativetrace-core").contains("narrativetrace-proxy");
  }

  @Test
  void exampleEcommerceDependsOnSpringMicrometerSlf4j() {
    var result =
        gradle(
            ":narrativetrace-examples:ecommerce:dependencies",
            "--configuration",
            "compileClasspath");
    var output = result.getOutput();

    assertThat(output)
        .contains("narrativetrace-spring")
        .contains("narrativetrace-micrometer")
        .contains("narrativetrace-slf4j");
  }

  @Test
  void junit5DependsOnCoreProxyDiagramsClarity() {
    var result =
        gradle(":narrativetrace-junit5:dependencies", "--configuration", "compileClasspath");
    var output = result.getOutput();

    assertThat(output)
        .contains("narrativetrace-core")
        .contains("narrativetrace-proxy")
        .contains("narrativetrace-diagrams")
        .contains("narrativetrace-clarity");
  }

  @Test
  void coreExposesApiToItsConsumers() {
    var result =
        gradle(":narrativetrace-servlet:dependencies", "--configuration", "compileClasspath");

    assertThat(result.getOutput())
        .as(
            "core declares api as `api`, so a consumer compiles against the annotations and"
                + " event model without naming the module itself")
        .contains("narrativetrace-api");
  }

  @Test
  void apiDependsOnNothing() {
    var result = gradle(":narrativetrace-api:dependencies", "--configuration", "runtimeClasspath");

    assertThat(result.getOutput())
        .as(
            "the API jar is what third parties compile against; every dependency it takes is one"
                + " they cannot refuse")
        .contains("No dependencies");
  }

  @Test
  void proxyDependsOnCore() {
    var result =
        gradle(":narrativetrace-proxy:dependencies", "--configuration", "compileClasspath");
    assertThat(result.getOutput()).contains("narrativetrace-core");
  }

  @Test
  void micronautDependsOnCoreProxy() {
    var result =
        gradle(":narrativetrace-micronaut:dependencies", "--configuration", "compileClasspath");
    var output = result.getOutput();

    assertThat(output).contains("narrativetrace-core").contains("narrativetrace-proxy");
  }

  @Test
  void micronautHttpDependsOnMicronautAndServlet() {
    var result =
        gradle(
            ":narrativetrace-micronaut-http:dependencies", "--configuration", "compileClasspath");
    var output = result.getOutput();

    assertThat(output)
        .contains("narrativetrace-micronaut")
        .contains("narrativetrace-servlet")
        .contains("narrativetrace-core");
  }

  @Test
  void jdependNotRegisteredOnMicronautModules() {
    var result = gradle(":narrativetrace-micronaut:tasks", "--all");
    assertThat(result.getOutput()).doesNotContain("jdependCheck");

    result = gradle(":narrativetrace-micronaut-http:tasks", "--all");
    assertThat(result.getOutput()).doesNotContain("jdependCheck");
  }

  @Test
  void fullCheckDryRunResolvesWithoutErrors() {
    var result = gradle("check", "--dry-run");
    var output = result.getOutput();

    assertThat(output)
        .contains(":narrativetrace-core:check")
        .contains(":narrativetrace-proxy:check")
        .contains(":narrativetrace-junit4:check")
        .contains(":narrativetrace-junit4-example:check")
        .contains(":narrativetrace-junit5:check")
        .contains(":narrativetrace-clarity:check")
        .contains(":narrativetrace-diagrams:check")
        .contains(":narrativetrace-slf4j:check")
        .contains(":narrativetrace-agent:check")
        .contains(":narrativetrace-spring:check")
        .contains(":narrativetrace-spring-web:check")
        .contains(":narrativetrace-servlet:check")
        .contains(":narrativetrace-opentelemetry:check")
        .contains(":narrativetrace-micrometer:check")
        .contains(":narrativetrace-examples:check")
        .contains(":narrativetrace-gradle-plugin:check")
        .contains(":narrativetrace-micronaut:check")
        .contains(":narrativetrace-micronaut-http:check");
  }

  @Test
  void parametersCompilerFlagPresent() {
    var result = gradle(":narrativetrace-core:printCompilerArgs");
    var output = result.getOutput();

    assertThat(output).contains("COMPILER_ARGS: [-parameters]");
  }

  @Test
  void java17SourceCompatibility() {
    var result = gradle(":narrativetrace-core:properties");
    var output = result.getOutput();

    assertThat(output).contains("sourceCompatibility: 17");
  }

  @Test
  void jdependTasksRegisteredOnProductionModule() {
    var result = gradle(":narrativetrace-core:tasks", "--all");
    var output = result.getOutput();

    assertThat(output).contains("jdepend").contains("jdependCheck");
  }

  @Test
  void jdependNotRegisteredOnExcludedModules() {
    var result = gradle(":narrativetrace-benchmarks:tasks", "--all");
    assertThat(result.getOutput()).doesNotContain("jdependCheck");

    result = gradle(":narrativetrace-jcstress:tasks", "--all");
    assertThat(result.getOutput()).doesNotContain("jdependCheck");
  }

  @Test
  void jdependCheckWiredIntoCheck() {
    var result = gradle(":narrativetrace-core:check", "--dry-run");
    assertThat(result.getOutput()).contains("jdependCheck");
  }

  @Test
  void jdependCrossModuleWiredIntoCheck() {
    var result = gradle(":narrativetrace-core:check", "--dry-run");
    assertThat(result.getOutput()).contains("jdependCrossModule");
  }

  @Test
  void jdependReportAndCrossModuleTasksExistAtRoot() {
    var result = gradle("tasks", "--group=verification");
    var output = result.getOutput();

    assertThat(output).contains("jdependReport").contains("jdependCrossModule");
  }

  @Test
  void dependencyReportTaskExists() {
    var result = gradle("tasks", "--group=verification");
    assertThat(result.getOutput()).contains("dependencyReport");
  }

  @Test
  void dependencyReportProducesOutputFile() {
    gradle("dependencyReport");
    var reportFile =
        new File(PROJECT_DIR, "build/reports/dependency-graph/module-dependencies.txt");
    assertThat(reportFile).exists();
    assertThat(reportFile.length()).isGreaterThan(0);
  }

  /**
   * The demo launcher ({@code demo.sh}) runs examples through their {@code installDist} start
   * scripts to keep Gradle noise out of the demo stream — every example must produce one.
   */
  @Test
  void everyExampleProducesAnInstallDistStartScript() {
    for (var example : List.of("ecommerce", "clarity", "minecraft", "library")) {
      gradle(":narrativetrace-examples:" + example + ":installDist");
      var script =
          new File(
              PROJECT_DIR,
              "narrativetrace-examples/%s/build/install/%s/bin/%s"
                  .formatted(example, example, example));
      assertThat(script).as("start script for %s", example).exists();
    }
  }
}
