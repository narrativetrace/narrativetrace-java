/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NarrativeTracePluginFunctionalTest {

  @Test
  void pluginCanBeApplied(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .build();

    assertThat(result.task(":tasks").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void extensionIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.register(\"checkExtension\") {\n"
            + "    doLast {\n"
            + "        val ext = project.extensions.getByName(\"narrativeTrace\")\n"
            + "        println(\"EXTENSION_FOUND: \" + ext.javaClass.simpleName)\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkExtension")
            .build();

    assertThat(result.getOutput()).contains("EXTENSION_FOUND:");
  }

  @Test
  void claritySubExtensionAccessible(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    clarity {\n"
            + "        minScore.set(0.80)\n"
            + "        maxHighIssues.set(0)\n"
            + "    }\n"
            + "}\n"
            + "tasks.register(\"checkClarity\") {\n"
            + "    doLast {\n"
            + "        val ext = project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
            + "        println(\"MIN_SCORE: \" + ext.clarity.minScore.get())\n"
            + "        println(\"MAX_HIGH: \" + ext.clarity.maxHighIssues.get())\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkClarity")
            .build();

    assertThat(result.getOutput()).contains("MIN_SCORE: 0.8");
    assertThat(result.getOutput()).contains("MAX_HIGH: 0");
  }

  @Test
  void pluginAddsParametersCompilerFlag(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.withType<JavaCompile> {\n"
            + "    doFirst {\n"
            + "        println(\"COMPILER_ARGS: \" + options.compilerArgs)\n"
            + "    }\n"
            + "}\n");
    writeJavaSource(projectDir);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileJava")
            .build();

    assertThat(result.getOutput()).contains("-parameters");
  }

  @Test
  void pluginAddsParametersFlagAlongsideUserFlag(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.withType<JavaCompile> {\n"
            + "    options.compilerArgs.add(\"-parameters\")\n"
            + "    doFirst {\n"
            + "        println(\"HAS_PARAMETERS: \" + (\"-parameters\" in options.compilerArgs))\n"
            + "    }\n"
            + "}\n");
    writeJavaSource(projectDir);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileJava")
            .build();

    assertThat(result.getOutput()).contains("HAS_PARAMETERS: true");
  }

  @Test
  void defaultModeAddsProxyDependency(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput())
        .contains("DEP: ai.narrativetrace:narrativetrace-api")
        .contains("DEP: ai.narrativetrace:narrativetrace-core")
        .contains("DEP: ai.narrativetrace:narrativetrace-proxy")
        .contains("DEP: ai.narrativetrace:narrativetrace-clarity")
        .contains("DEP: ai.narrativetrace:narrativetrace-diagrams")
        .contains("DEP: ai.narrativetrace:narrativetrace-junit5");
  }

  @Test
  void libraryVersionOverrideAppliesToManagedDependencies(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { libraryVersion.set(\"9.9.9-TEST\") }\n" + listDepsWithVersionTask());

    var result = runGradle(projectDir, "listDepsV");

    assertThat(result.getOutput())
        .contains("DEPV: ai.narrativetrace:narrativetrace-api:9.9.9-TEST")
        .contains("DEPV: ai.narrativetrace:narrativetrace-core:9.9.9-TEST")
        .contains("DEPV: ai.narrativetrace:narrativetrace-junit5:9.9.9-TEST");
  }

  @Test
  void managedDependenciesUseEmbeddedVersionWhenNoOverride(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, listDepsWithVersionTask());

    var result = runGradle(projectDir, "listDepsV");

    assertThat(result.getOutput())
        .contains("DEPV: ai.narrativetrace:narrativetrace-core:" + VersionResolver.resolve())
        .doesNotContain("9.9.9-TEST");
  }

  @Test
  void agentModeDoesNotAddProxy(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { mode.set(\"agent\") }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput())
        .contains("DEP: ai.narrativetrace:narrativetrace-agent")
        .doesNotContain("DEP: ai.narrativetrace:narrativetrace-proxy");
  }

  @Test
  void springModeAddsSpringAndProxy(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { mode.set(\"spring\") }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput())
        .contains("DEP: ai.narrativetrace:narrativetrace-spring")
        .contains("DEP: ai.narrativetrace:narrativetrace-proxy");
  }

  @Test
  void invalidTestFrameworkThrowsError(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { testFramework.set(\"testng\") }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid narrativeTrace testFramework 'testng'");
  }

  @Test
  void invalidModeThrowsError(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { mode.set(\"invalid\") }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid narrativeTrace mode 'invalid'");
  }

  @Test
  void junit4TestFrameworkConfigured(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { testFramework.set(\"junit4\") }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput())
        .contains("DEP: ai.narrativetrace:narrativetrace-junit4")
        .doesNotContain("DEP: ai.narrativetrace:narrativetrace-junit5");
  }

  @Test
  void modulesSubExtensionAccessible(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    modules {\n"
            + "        slf4j.set(true)\n"
            + "        micrometer.set(true)\n"
            + "    }\n"
            + "}\n"
            + "tasks.register(\"checkModules\") {\n"
            + "    doLast {\n"
            + "        val ext = project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
            + "        println(\"SLF4J: \" + ext.modules.slf4j.get())\n"
            + "        println(\"MICROMETER: \" + ext.modules.micrometer.get())\n"
            + "        println(\"SERVLET: \" + ext.modules.servlet.get())\n"
            + "        println(\"SPRING_WEB: \" + ext.modules.springWeb.get())\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkModules")
            .build();

    assertThat(result.getOutput()).contains("SLF4J: true");
    assertThat(result.getOutput()).contains("MICROMETER: true");
    assertThat(result.getOutput()).contains("SERVLET: false");
    assertThat(result.getOutput()).contains("SPRING_WEB: false");
  }

  @Test
  void agentSubExtensionAccessible(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    agent {\n"
            + "        packages.set(listOf(\"com.example.app\", \"com.example.lib\"))\n"
            + "    }\n"
            + "}\n"
            + "tasks.register(\"checkAgent\") {\n"
            + "    doLast {\n"
            + "        val ext = project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
            + "        println(\"PACKAGES: \" + ext.agent.packages.get())\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkAgent")
            .build();

    assertThat(result.getOutput()).contains("com.example.app");
    assertThat(result.getOutput()).contains("com.example.lib");
  }

  @Test
  void slf4jModuleAddedWhenEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { modules { slf4j.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-slf4j");
  }

  @Test
  void micrometerModuleAddedWhenEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { micrometer.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-micrometer");
  }

  @Test
  void servletModuleAddedWhenEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { servlet.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-servlet");
  }

  @Test
  void springWebImpliesServlet(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { mode.set(\"spring\")\nmodules { springWeb.set(true) } }\n"
            + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-spring-web");
    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-servlet");
  }

  @Test
  void opentelemetryModuleAddedWhenEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { opentelemetry.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-opentelemetry");
  }

  @Test
  void micronautModuleAddedWhenEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { micronaut.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-micronaut");
  }

  @Test
  void micronautHttpImpliesMicronaut(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { micronautHttp.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-micronaut-http");
    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-micronaut");
  }

  @Test
  void springWebWithNonSpringModeWarns(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { modules { springWeb.set(true) } }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("springWeb is enabled but mode is not 'spring'");
  }

  @Test
  void defaultScopeIsTestImplementation(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-core");
    assertThat(result.getOutput()).doesNotContain("IMPL_DEP: ai.narrativetrace:");
  }

  @Test
  void productionScopeUsesImplementation(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { scope.set(\"production\") }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput()).contains("IMPL_DEP: ai.narrativetrace:narrativetrace-core");
    // test framework always on testImplementation
    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-junit5");
  }

  @Test
  void testFrameworkAlwaysOnTestImplementation(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { scope.set(\"production\") }\n" + listDepsTask());

    var result = runGradle(projectDir, "listDeps");

    assertThat(result.getOutput())
        .doesNotContain("IMPL_DEP: ai.narrativetrace:narrativetrace-junit5");
    assertThat(result.getOutput()).contains("DEP: ai.narrativetrace:narrativetrace-junit5");
  }

  @Test
  void invalidScopeThrowsError(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { scope.set(\"compile\") }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid narrativeTrace scope 'compile'");
  }

  @Test
  void agentModeCreatesAgentConfiguration(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { mode.set(\"agent\") }\n"
            + "tasks.register(\"checkAgentConfig\") {\n"
            + "    doLast {\n"
            + "        val cfg = configurations.getByName(\"narrativeTraceAgent\")\n"
            + "        cfg.dependencies.forEach {\n"
            + "            println(\"AGENT_DEP: \" + it.group + \":\" + it.name)\n"
            + "        }\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkAgentConfig");

    assertThat(result.getOutput()).contains("AGENT_DEP: ai.narrativetrace:narrativetrace-agent");
  }

  @Test
  void agentModeConfigurationIsNotTransitive(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { mode.set(\"agent\") }\n"
            + "tasks.register(\"checkAgentTransitive\") {\n"
            + "    doLast {\n"
            + "        val cfg = configurations.getByName(\"narrativeTraceAgent\")\n"
            + "        println(\"TRANSITIVE: \" + cfg.isTransitive)\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkAgentTransitive");

    assertThat(result.getOutput()).contains("TRANSITIVE: false");
  }

  @Test
  void agentModeIncludesPackagesInConfig(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    mode.set(\"agent\")\n"
            + "    agent { packages.set(listOf(\"com.example.app\", \"com.example.lib\")) }\n"
            + "}\n"
            + "tasks.register(\"checkAgentPackages\") {\n"
            + "    doLast {\n"
            + "        val ext = project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
            + "        println(\"PACKAGES: \" + ext.agent.packages.get())\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkAgentPackages");

    assertThat(result.getOutput()).contains("com.example.app");
    assertThat(result.getOutput()).contains("com.example.lib");
  }

  @Test
  void proxyModeDoesNotCreateAgentConfiguration(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.register(\"checkAgentConfig\") {\n"
            + "    doLast {\n"
            + "        val found = configurations.findByName(\"narrativeTraceAgent\")\n"
            + "        println(\"AGENT_CONFIG_EXISTS: \" + (found != null))\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkAgentConfig");

    assertThat(result.getOutput()).contains("AGENT_CONFIG_EXISTS: false");
  }

  @Test
  void formatPropertyForwardedToTestTask(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { format.set(\"mermaid\") }\n"
            + "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"FORMAT: \" + testTask.systemProperties[\"narrativetrace.format\"])\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkTestConfig");

    assertThat(result.getOutput()).contains("FORMAT: mermaid");
  }

  @Test
  void tracingLevelForwardedToTestTask(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { tracingLevel.set(\"DETAIL\") }\n"
            + "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"LEVEL: \" + testTask.systemProperties[\"narrativetrace.level\"])\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkTestConfig");

    assertThat(result.getOutput()).contains("LEVEL: DETAIL");
  }

  @Test
  void formatNotForwardedWhenNotSet(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"FORMAT: \" + testTask.systemProperties[\"narrativetrace.format\"])\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkTestConfig");

    assertThat(result.getOutput()).contains("FORMAT: null");
  }

  @Test
  void invalidFormatThrowsError(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { format.set(\"xml\") }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid narrativeTrace format 'xml'");
  }

  @Test
  void invalidTracingLevelThrowsError(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { tracingLevel.set(\"VERBOSE\") }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid narrativeTrace tracingLevel 'VERBOSE'");
  }

  @Test
  void pluginSetsOutputSystemProperties(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"SYS_OUTPUT: \" + testTask.systemProperties[\"narrativetrace.output\"])\n"
            + "        println(\"SYS_DIR: \" + testTask.systemProperties[\"narrativetrace.outputDir\"])\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkTestConfig")
            .build();

    assertThat(result.getOutput()).contains("SYS_OUTPUT: true");
    assertThat(result.getOutput()).contains("SYS_DIR:");
    assertThat(result.getOutput()).contains("narrativetrace");
  }

  @Test
  void glossaryScanTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "tasks", "--group", "verification");

    assertThat(result.getOutput()).contains("glossaryScan");
  }

  @Test
  void glossaryHarvestIsOffUnlessEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, printGlossaryPropertiesTask());

    var result = runGradle(projectDir, "checkGlossaryConfig");

    assertThat(result.getOutput()).contains("SYS_GLOSSARY: null");
    assertThat(result.getOutput()).contains("SYS_GLOSSARY_DIR: null");
  }

  @Test
  void enabledGlossaryHarvestTargetsTheRepositoryRoot(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n    glossary.set(true)\n}\n" + printGlossaryPropertiesTask());

    var result = runGradle(projectDir, "checkGlossaryConfig");

    assertThat(result.getOutput()).contains("SYS_GLOSSARY: true");
    assertThat(result.getOutput())
        .contains("SYS_GLOSSARY_DIR: " + projectDir.toRealPath().toString());
  }

  private String printGlossaryPropertiesTask() {
    return "tasks.register(\"checkGlossaryConfig\") {\n"
        + "    doLast {\n"
        + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
        + "        println(\"SYS_GLOSSARY: \" + testTask.systemProperties[\"narrativetrace.glossary\"])\n"
        + "        println(\"SYS_GLOSSARY_DIR: \" + testTask.systemProperties[\"narrativetrace.glossaryDir\"])\n"
        + "    }\n"
        + "}\n";
  }

  @Test
  void approveNarrativesTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "tasks", "--group", "verification");

    assertThat(result.getOutput()).contains("approveNarratives");
  }

  @Test
  void approvalModeIsOffUnlessEnabled(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, printApprovalPropertiesTask());

    var result = runGradle(projectDir, "checkApprovalConfig");

    assertThat(result.getOutput()).contains("SYS_APPROVAL: null");
    assertThat(result.getOutput()).contains("SYS_APPROVED_DIR: null");
  }

  @Test
  void enabledApprovalModeForwardsTheNarrativesDirectory(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n    approval.set(true)\n}\n" + printApprovalPropertiesTask());

    var result = runGradle(projectDir, "checkApprovalConfig");

    assertThat(result.getOutput()).contains("SYS_APPROVAL: true");
    assertThat(result.getOutput())
        .contains(
            "SYS_APPROVED_DIR: "
                + projectDir.toRealPath().resolve("src/test/narratives").toString());
  }

  @Test
  void approveNarrativesPromotesReceivedFilesToBaselines(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");
    var scenarioDir = projectDir.resolve("src/test/narratives/OrderTest");
    java.nio.file.Files.createDirectories(scenarioDir);
    java.nio.file.Files.writeString(scenarioDir.resolve("trip_settles.received.nt"), "structure\n");

    var result = runGradle(projectDir, "approveNarratives");

    assertThat(result.getOutput()).contains("Approved: ");
    assertThat(scenarioDir.resolve("trip_settles.approved.nt")).exists();
    assertThat(scenarioDir.resolve("trip_settles.received.nt")).doesNotExist();
  }

  private String printApprovalPropertiesTask() {
    return "tasks.register(\"checkApprovalConfig\") {\n"
        + "    doLast {\n"
        + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
        + "        println(\"SYS_APPROVAL: \" + testTask.systemProperties[\"narrativetrace.approval\"])\n"
        + "        println(\"SYS_APPROVED_DIR: \" + testTask.systemProperties[\"narrativetrace.approvedDir\"])\n"
        + "    }\n"
        + "}\n";
  }

  @Test
  void customOutputDirIsForwarded(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    outputDir.set(layout.buildDirectory.dir(\"custom-traces\"))\n"
            + "}\n"
            + "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"SYS_DIR: \" + testTask.systemProperties[\"narrativetrace.outputDir\"])\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkTestConfig")
            .build();

    assertThat(result.getOutput()).contains("custom-traces");
  }

  @Test
  void junit5ModeConfiguresUseJUnitPlatform(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "tasks.register(\"checkTestPlatform\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        val framework = testTask.testFramework\n"
            + "        println(\"FRAMEWORK: \" + framework.javaClass.simpleName)\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkTestPlatform");

    assertThat(result.getOutput()).contains("FRAMEWORK: JUnitPlatformTestFramework");
  }

  @Test
  void junit4ModeDoesNotConfigureJUnitPlatform(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace { testFramework.set(\"junit4\") }\n"
            + "tasks.register(\"checkTestPlatform\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        val framework = testTask.testFramework\n"
            + "        println(\"FRAMEWORK: \" + framework.javaClass.simpleName)\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "checkTestPlatform");

    assertThat(result.getOutput()).doesNotContain("FRAMEWORK: JUnitPlatformTestFramework");
  }

  @Test
  void disabledPluginDoesNotConfigureTests(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    enabled.set(false)\n"
            + "}\n"
            + "tasks.register(\"checkTestConfig\") {\n"
            + "    doLast {\n"
            + "        val testTask = tasks.named(\"test\", Test::class.java).get()\n"
            + "        println(\"SYS_OUTPUT: \" + testTask.systemProperties[\"narrativetrace.output\"])\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("checkTestConfig")
            .build();

    assertThat(result.getOutput()).contains("SYS_OUTPUT: null");
  }

  @Test
  void clarityCheckTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build();

    assertThat(result.getOutput()).contains("clarityCheck");
  }

  @Test
  void clarityCheckDependsOnTest(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "--dry-run")
            .build();

    var output = result.getOutput();
    int testIndex = output.indexOf(":test ");
    int clarityIndex = output.indexOf(":clarityCheck ");
    assertThat(testIndex).isGreaterThanOrEqualTo(0);
    assertThat(clarityIndex).isGreaterThan(testIndex);
  }

  @Test
  void checkDependsOnClarityCheck(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("check", "--dry-run")
            .build();

    assertThat(result.getOutput()).contains(":clarityCheck ");
  }

  @Test
  void clarityCheckPassesWhenNoThreshold(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");
    writeClarityJson(projectDir, scenarioJson("Test", 0.50));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void clarityCheckPassesWhenScoreMeetsThreshold(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { minScore.set(0.80) } }\n");
    writeClarityJson(projectDir, scenarioJson("Test", 0.85));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void clarityCheckFailsWhenScoreBelowThreshold(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { minScore.set(0.80) } }\n");
    writeClarityJson(projectDir, scenarioJson("Checkout flow", 0.60));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Clarity check failed");
    assertThat(result.getOutput()).contains("Checkout flow");
    assertThat(result.getOutput()).contains("0.60");
  }

  @Test
  void clarityCheckReportsAllFailingScenarios(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { minScore.set(0.80) } }\n");
    writeClarityJson(projectDir, scenarioJson("First", 0.50) + "," + scenarioJson("Second", 0.60));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .buildAndFail();

    assertThat(result.getOutput()).contains("First").contains("Second");
  }

  @Test
  void clarityCheckEnforcesMaxHighIssues(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { maxHighIssues.set(0) } }\n");
    var json =
        "{\"version\":\"1.0\",\"scenarios\":["
            + "{\"name\":\"Test\",\"overallScore\":0.90"
            + ",\"methodNameScore\":0.90,\"classNameScore\":0.90"
            + ",\"parameterNameScore\":0.90,\"structuralScore\":0.90,\"cohesionScore\":0.90"
            + ",\"issues\":[{\"category\":\"param-name\",\"element\":\"x\""
            + ",\"suggestion\":\"fix\",\"severity\":\"HIGH\",\"occurrences\":3,\"impactScore\":9.00}]"
            + "}]}";
    writeClarityJsonRaw(projectDir, json);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .buildAndFail();

    assertThat(result.getOutput()).contains("HIGH issues");
  }

  @Test
  void clarityCheckReportsSuiteIssuesAsAdvisoryByDefault(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");
    writeClarityJsonRaw(projectDir, suiteIssueJson());

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome()).isEqualTo(SUCCESS);
    assertThat(result.getOutput())
        .contains("1 suite-level clarity issue")
        .contains("billing.OverdraftService.openAccountWithOverdraft")
        .contains("use canonical term 'overdraft account'");
  }

  @Test
  void clarityCheckFailsWhenSuiteIssuesExceedOptInMax(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { maxSuiteIssues.set(0) } }\n");
    writeClarityJsonRaw(projectDir, suiteIssueJson());

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .buildAndFail();

    assertThat(result.getOutput())
        .contains("Clarity check failed")
        .contains("suite issues (max 0)");
  }

  @Test
  void clarityCheckWarnOnlyDowngradesSuiteIssueFailure(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { clarity { maxSuiteIssues.set(0)\nwarnOnly.set(true) } }\n");
    writeClarityJsonRaw(projectDir, suiteIssueJson());

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome()).isEqualTo(SUCCESS);
    assertThat(result.getOutput()).contains("suite issues (max 0)");
  }

  private String suiteIssueJson() {
    return "{\"version\":\"1.1\",\"scenarios\":[]"
        + ",\"suiteIssues\":[{\"category\":\"non-canonical-term\""
        + ",\"element\":\"billing.OverdraftService.openAccountWithOverdraft\""
        + ",\"suggestion\":\"use canonical term 'overdraft account'\""
        + ",\"severity\":\"MEDIUM\",\"occurrences\":2,\"impactScore\":4.00}]}";
  }

  @Test
  void clarityCheckWarnsWhenWarnOnly(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace { clarity { minScore.set(0.80)\nwarnOnly.set(true) } }\n");
    writeClarityJson(projectDir, scenarioJson("Test", 0.50));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void clarityCheckIsUpToDateOnSecondRun(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");
    writeClarityJson(projectDir, scenarioJson("Test", 0.90));

    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("clarityCheck", "-x", "test")
        .build();

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome())
        .isEqualTo(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE);
  }

  @Test
  void clarityCheckSkipsWhenJsonMissing(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace { clarity { minScore.set(0.80) } }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.getOutput()).contains("No clarity-results.json found");
  }

  @Test
  void clarityCheckFailsOnMalformedJson(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");
    writeClarityJsonRaw(projectDir, "{not valid}");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Failed to parse clarity results");
  }

  @Test
  void disabledPluginSkipsClarityCheck(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "narrativeTrace {\n" + "    enabled.set(false)\n" + "}\n");
    writeClarityJson(projectDir, scenarioJson("Test", 0.90));

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityCheck", "-x", "test")
            .build();

    assertThat(result.task(":clarityCheck").getOutcome())
        .isEqualTo(org.gradle.testkit.runner.TaskOutcome.SKIPPED);
  }

  @Test
  void pluginWithoutJavaPluginDoesNotCrash(@TempDir Path projectDir) throws IOException {
    writeBuildFileWithoutJava(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks")
            .build();

    assertThat(result.task(":tasks").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void pluginWithoutJavaPluginDoesNotRegisterClarityTasks(@TempDir Path projectDir)
      throws IOException {
    writeBuildFileWithoutJava(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build();

    assertThat(result.getOutput()).doesNotContain("clarityCheck");
    assertThat(result.getOutput()).doesNotContain("clarityScan");
  }

  @Test
  void pluginWorksInSubproject(@TempDir Path projectDir) throws IOException {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"multi-project\"\ninclude(\"sub\")");
    Files.writeString(projectDir.resolve("build.gradle.kts"), "");
    var subDir = projectDir.resolve("sub");
    Files.createDirectories(subDir);
    Files.writeString(
        subDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"ai.narrativetrace\")\n"
            + "}\n"
            + "repositories { mavenCentral() }\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":sub:tasks", "--all")
            .build();

    assertThat(result.getOutput()).contains("clarityCheck");
  }

  @Test
  void tasksVisibleInDryRun(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("check", "--dry-run")
            .build();

    assertThat(result.getOutput()).contains(":clarityCheck ");
    assertThat(result.getOutput()).contains(":test ");
  }

  @Test
  void clarityScanTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build();

    assertThat(result.getOutput()).contains("clarityScan");
  }

  @Test
  void clarityScanTaskDependsOnClasses(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");
    writeJavaSource(projectDir);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityScan", "--dry-run")
            .build();

    assertThat(result.getOutput()).contains(":classes");
    assertThat(result.getOutput()).contains(":clarityScan");
  }

  @Test
  void clarityScanTaskHasCorrectConfiguration(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "afterEvaluate {\n"
            + "    tasks.named<JavaExec>(\"clarityScan\") {\n"
            + "        doFirst {\n"
            + "            println(\"MAIN_CLASS: \" + mainClass.get())\n"
            + "            println(\"ARGS: \" + args)\n"
            + "        }\n"
            + "    }\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityScan", "--dry-run")
            .build();

    assertThat(result.getOutput()).contains(":clarityScan");
  }

  @Test
  void clarityScanRunsTheGlossaryAwareEntryPoint(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "afterEvaluate {\n"
            + "    println(\"MAIN_CLASS: \" + "
            + "tasks.named<JavaExec>(\"clarityScan\").get().mainClass.get())\n"
            + "}\n");

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("clarityScan", "--dry-run")
            .build();

    assertThat(result.getOutput())
        .contains("MAIN_CLASS: ai.narrativetrace.glossary.GlossaryAwareClarityScannerMain");
  }

  private String scenarioJson(String name, double score) {
    return String.format(
        "{\"name\":\"%s\",\"overallScore\":%.2f"
            + ",\"methodNameScore\":%.2f,\"classNameScore\":%.2f"
            + ",\"parameterNameScore\":%.2f,\"structuralScore\":%.2f,\"cohesionScore\":%.2f"
            + ",\"issues\":[]}",
        name, score, score, score, score, score, score);
  }

  private void writeClarityJson(Path projectDir, String scenariosContent) throws IOException {
    writeClarityJsonRaw(
        projectDir, "{\"version\":\"1.0\",\"scenarios\":[" + scenariosContent + "]}");
  }

  private void writeClarityJsonRaw(Path projectDir, String json) throws IOException {
    var dir = projectDir.resolve("build/narrativetrace");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("clarity-results.json"), json);
  }

  @Test
  void theDocumentedOneLinerRunsARealTestGreen(@TempDir Path projectDir) throws IOException {
    writeStandaloneJunit5Project(projectDir);
    writeJunit5Test(projectDir);

    var result = runGradle(projectDir, "test");

    assertThat(result.task(":test").getOutcome())
        .as("applying the plugin and running `gradle test` must not need a second dependency")
        .isEqualTo(SUCCESS);
    assertThat(result.getOutput()).doesNotContain("Cannot create Launcher");
  }

  @Test
  void theTestReallyRanRatherThanBeingSkipped(@TempDir Path projectDir) throws IOException {
    writeStandaloneJunit5Project(projectDir);
    writeJunit5Test(projectDir);

    // A test task with nothing to run is also SUCCESS, so prove the engine executed a method.
    var result = runGradle(projectDir, "test", "--info");

    assertThat(result.getOutput()).contains("ENGINE_EXECUTED_THIS_TEST");
  }

  @Test
  void aJunit4ProjectGetsNoJupiterEngine(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    testFramework.set(\"junit4\")\n"
            + "}\n"
            + listTestRuntimeOnlyTask());

    var result = runGradle(projectDir, "listRuntimeDeps");

    assertThat(result.getOutput()).doesNotContain("junit-jupiter-engine");
  }

  @Test
  void aJunit5ProjectDeclaresTheEngineOnTheTestRuntimeClasspath(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, listTestRuntimeOnlyTask());

    var result = runGradle(projectDir, "listRuntimeDeps");

    assertThat(result.getOutput())
        .contains("RUNTIME_DEP: org.junit.jupiter:junit-jupiter-engine:5.11.4");
  }

  private String listTestRuntimeOnlyTask() {
    return "tasks.register(\"listRuntimeDeps\") {\n"
        + "    doLast {\n"
        + "        configurations.getByName(\"testRuntimeOnly\").dependencies.forEach {\n"
        + "            println(\"RUNTIME_DEP: \" + it.group + \":\" + it.name + \":\" + it.version)\n"
        + "        }\n"
        + "    }\n"
        + "}\n";
  }

  /**
   * A project that applies the plugin and nothing else, with the NarrativeTrace artifacts excluded
   * from resolution.
   *
   * <p>They are not published to any repository an isolated TestKit project can see, and they are
   * not what the bug was about: the engine was missing, not the library. {@code junit-jupiter-api}
   * stands in for what {@code narrativetrace-junit5} contributes transitively (that module declares
   * it as {@code api}, which {@code BuildConfigurationTest} pins separately). Nothing here supplies
   * an engine — so if the plugin stops adding one, these tests fail with the exact error a user
   * reported.
   */
  private void writeStandaloneJunit5Project(Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "configurations.all { exclude(group = \"ai.narrativetrace\") }\n"
            + "dependencies {\n"
            + "    testImplementation(\"org.junit.jupiter:junit-jupiter-api:5.11.4\")\n"
            + "}\n"
            + "tasks.test { testLogging.showStandardStreams = true }\n");
  }

  private void writeJunit5Test(Path projectDir) throws IOException {
    var testDir = projectDir.resolve("src/test/java");
    Files.createDirectories(testDir);
    Files.writeString(
        testDir.resolve("SmokeTest.java"),
        "import org.junit.jupiter.api.Test;\n"
            + "class SmokeTest {\n"
            + "  @Test\n"
            + "  void runs() {\n"
            + "    System.out.println(\"ENGINE_EXECUTED_THIS_TEST\");\n"
            + "  }\n"
            + "}\n");
  }

  private void writeJavaSource(Path projectDir) throws IOException {
    var srcDir = projectDir.resolve("src/main/java");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("Dummy.java"), "public class Dummy {}");
  }

  private void writeBuildFileWithoutJava(Path projectDir, String extraConfig) throws IOException {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-project\"");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n" + "    id(\"ai.narrativetrace\")\n" + "}\n" + extraConfig);
  }

  private void writeBuildFile(Path projectDir, String extraConfig) throws IOException {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-project\"");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"ai.narrativetrace\")\n"
            + "}\n"
            + "repositories { mavenCentral() }\n"
            + extraConfig);
  }

  private org.gradle.testkit.runner.BuildResult runGradle(Path projectDir, String... args)
      throws IOException {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .build();
  }

  private String listDepsWithVersionTask() {
    return "tasks.register(\"listDepsV\") {\n"
        + "    doLast {\n"
        + "        (configurations.getByName(\"testImplementation\").dependencies +"
        + " configurations.getByName(\"implementation\").dependencies).forEach {\n"
        + "            println(\"DEPV: \" + it.group + \":\" + it.name + \":\" + it.version)\n"
        + "        }\n"
        + "    }\n"
        + "}\n";
  }

  private String listDepsTask() {
    return "tasks.register(\"listDeps\") {\n"
        + "    doLast {\n"
        + "        configurations.getByName(\"testImplementation\").dependencies.forEach {\n"
        + "            println(\"DEP: \" + it.group + \":\" + it.name)\n"
        + "        }\n"
        + "        configurations.getByName(\"implementation\").dependencies.forEach {\n"
        + "            println(\"IMPL_DEP: \" + it.group + \":\" + it.name)\n"
        + "        }\n"
        + "    }\n"
        + "}\n";
  }
}
