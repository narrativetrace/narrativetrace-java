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
import java.util.List;
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
            + "        val ext ="
            + " project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
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
            + "        val ext ="
            + " project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
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
            + "        val ext ="
            + " project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
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
            + "        val ext ="
            + " project.extensions.getByType(ai.narrativetrace.gradle.NarrativeTraceExtension::class.java)\n"
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
            + "        println(\"FORMAT: \" +"
            + " testTask.systemProperties[\"narrativetrace.format\"])\n"
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
            + "        println(\"FORMAT: \" +"
            + " testTask.systemProperties[\"narrativetrace.format\"])\n"
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
            + "        println(\"SYS_OUTPUT: \" +"
            + " testTask.systemProperties[\"narrativetrace.output\"])\n"
            + "        println(\"SYS_DIR: \" +"
            + " testTask.systemProperties[\"narrativetrace.outputDir\"])\n"
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
        + "        println(\"SYS_GLOSSARY: \" +"
        + " testTask.systemProperties[\"narrativetrace.glossary\"])\n"
        + "        println(\"SYS_GLOSSARY_DIR: \" +"
        + " testTask.systemProperties[\"narrativetrace.glossaryDir\"])\n"
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
        + "        println(\"SYS_APPROVAL: \" +"
        + " testTask.systemProperties[\"narrativetrace.approval\"])\n"
        + "        println(\"SYS_APPROVED_DIR: \" +"
        + " testTask.systemProperties[\"narrativetrace.approvedDir\"])\n"
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
            + "        println(\"SYS_DIR: \" +"
            + " testTask.systemProperties[\"narrativetrace.outputDir\"])\n"
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
            + "        println(\"SYS_OUTPUT: \" +"
            + " testTask.systemProperties[\"narrativetrace.output\"])\n"
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
        "{\"version\":\"1.0\",\"scenarios\":[{\"name\":\"Test\",\"overallScore\":0.90"
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

  @Test
  void narrativetraceDoctorTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "tasks", "--group", "verification");

    assertThat(result.getOutput()).contains("narrativetraceDoctor");
  }

  @Test
  void narrativetraceDoctorWritesAWellFormedJsonReport(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.task(":narrativetraceDoctor").getOutcome()).isEqualTo(SUCCESS);
    var reportFile = projectDir.resolve("build/narrativetrace/doctor-report.json");
    assertThat(reportFile).exists();
    var json = Files.readString(reportFile);
    assertThat(json).contains("\"findings\"").contains("\"exitCode\"");
  }

  @Test
  void narrativetraceDoctorNeverFailsTheBuildOnFindings(@TempDir Path projectDir)
      throws IOException {
    // An otherwise-untouched project has plenty to find (no source, no test framework wired) —
    // exactly the case the task must survive without failing the build.
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.task(":narrativetraceDoctor").getOutcome()).isEqualTo(SUCCESS);
  }

  @Test
  void narrativetraceDoctorHonoursACustomOutputDir(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace {\n    outputDir.set(layout.buildDirectory.dir(\"nt\"))\n}\n");

    runGradle(projectDir, "narrativetraceDoctor");

    assertThat(projectDir.resolve("build/nt/doctor-report.json")).exists();
  }

  // --- narrativetraceDoctor: the twelfth check ------------------------------------------------

  @Test
  void narrativetraceDoctorSaysTheSkillsAreNotInstalledWhenItCanResolveTheCarrier(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.task(":narrativetraceDoctor").getOutcome()).isEqualTo(SUCCESS);
    assertThat(result.getOutput())
        .contains("[FAIL] config.skills-installed")
        .contains("narrativetraceInit --diff");
  }

  @Test
  void narrativetraceDoctorSaysTheSkillsAreCurrentRightAfterAnInstall(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    runGradle(projectDir, "narrativetraceInit");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.getOutput())
        .contains("[PASS] config.skills-installed")
        .contains(CARRIER_VERSION);
  }

  /**
   * Offline: nothing provides the carrier, so the doctor cannot compare and says so. The build is
   * green either way — this task never fails on findings — but the FINDING must not be a failure,
   * or an offline diagnosis would report a defect nobody introduced.
   */
  @Test
  void narrativetraceDoctorCannotTellWhenNothingProvidesTheCarrier(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.getOutput())
        .contains("[PASS] config.skills-installed")
        .contains("could not be resolved");
  }

  @Test
  void narrativetraceDoctorReportsSkillsInstalledFromAnOlderCarrier(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    runGradle(projectDir, "narrativetraceInit");
    writeStaleSkill(projectDir);

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.getOutput())
        .contains("[FAIL] config.skills-installed")
        .contains("installed from " + STALE_COORDINATE)
        .contains("resolves ai.narrativetrace:narrativetrace-skills:" + CARRIER_VERSION);
  }

  @Test
  void narrativetraceDoctorNamesTheOneSkillAnInstallIsMissing(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    runGradle(projectDir, "narrativetraceInit");
    deleteRecursively(projectDir.resolve(".agents/skills/narrativetrace-doctor"));

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.getOutput())
        .contains("[FAIL] config.skills-installed")
        .contains("narrativetrace-doctor is missing");
  }

  @Test
  void narrativetraceDoctorReportsAForeignSkillDirectoryWithoutCountingIt(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    runGradle(projectDir, "narrativetraceInit");
    Files.writeString(projectDir.resolve(DOCTOR_PAGE), "---\nname: mine\n---\n\nmine, thanks\n");

    var result = runGradle(projectDir, "narrativetraceDoctor");

    assertThat(result.getOutput()).contains("[FAIL] config.skills-installed").contains("not ours");
  }

  /** The finding's own field: the skill an agent should follow next, in the JSON report. */
  @Test
  void narrativetraceDoctorsJsonReportNamesTheSkillThatFixesEachFinding(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    runGradle(projectDir, "narrativetraceDoctor");

    var json = Files.readString(projectDir.resolve("build/narrativetrace/doctor-report.json"));
    assertThat(json).contains("\"skill\": \"narrativetrace-doctor\"").contains("\"skill\": null");
  }

  private static void deleteRecursively(Path root) throws IOException {
    try (var walk = Files.walk(root)) {
      for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
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
        .contains("RUNTIME_DEP: org.junit.jupiter:junit-jupiter-engine:null")
        .contains("RUNTIME_FLOOR: org.junit.jupiter:junit-jupiter-engine:5.11.4");
  }

  /**
   * Spring Boot's io.spring.dependency-management plugin lets a directly declared version override
   * its managed one. A pinned {@code junit-jupiter-engine:5.11.4} beside a BOM-managed {@code
   * junit-jupiter-api:5.12.2} split the pair and the test task discovered nothing — found building
   * the Spring Boot Tier B case. Declared as a floor, the managed version wins for both.
   */
  @Test
  void aBomManagedJunitIsNotDowngradedByThePluginsFloor(@TempDir Path projectDir)
      throws IOException {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-project\"");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.spring.dependency-management\") version \"1.1.7\"\n"
            + "    id(\"ai.narrativetrace\")\n"
            + "}\n"
            + "repositories { mavenCentral() }\n"
            + "configurations.all { exclude(group = \"ai.narrativetrace\") }\n"
            + "dependencyManagement { imports { mavenBom(\"org.junit:junit-bom:5.12.2\") } }\n"
            + "dependencies { testImplementation(\"org.junit.jupiter:junit-jupiter-api\") }\n"
            + "tasks.register(\"resolvedJunit\") {\n"
            + "    doLast {\n"
            + "        configurations.getByName(\"testRuntimeClasspath\").resolvedConfiguration\n"
            + "            .resolvedArtifacts.map { it.moduleVersion.id }\n"
            + "            .filter { it.group.startsWith(\"org.junit\") }\n"
            + "            .forEach { println(\"RESOLVED: \" + it.name + \":\" + it.version) }\n"
            + "    }\n"
            + "}\n");

    var result = runGradle(projectDir, "resolvedJunit");

    assertThat(result.getOutput())
        .contains("RESOLVED: junit-jupiter-api:5.12.2")
        .contains("RESOLVED: junit-jupiter-engine:5.12.2")
        .contains("RESOLVED: junit-platform-launcher:1.12.2")
        .doesNotContain("5.11.4")
        .doesNotContain("1.11.4");
  }

  /**
   * Gradle 8 supplies a version of the launcher itself when only the engine is declared (a
   * deprecated behaviour it warns about every run); Gradle 9 removes that auto-management, and
   * {@code useJUnitPlatform()} then fails the test process before any engine or extension runs
   * ("Could not start Gradle Test Executor 1: Failed to load JUnit Platform" — reproduced against a
   * real Gradle 9.0.0 run). The plugin must declare the launcher itself rather than ride on that
   * warned-about default.
   */
  @Test
  void aJunit5ProjectDeclaresThePlatformLauncherOnTheTestRuntimeClasspath(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, listTestRuntimeOnlyTask());

    var result = runGradle(projectDir, "listRuntimeDeps");

    assertThat(result.getOutput())
        .contains("RUNTIME_DEP: org.junit.platform:junit-platform-launcher:null")
        .contains("RUNTIME_FLOOR: org.junit.platform:junit-platform-launcher:1.11.4");
  }

  @Test
  void aJunit4ProjectGetsNoPlatformLauncherEither(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir,
        "narrativeTrace {\n"
            + "    testFramework.set(\"junit4\")\n"
            + "}\n"
            + listTestRuntimeOnlyTask());

    var result = runGradle(projectDir, "listRuntimeDeps");

    assertThat(result.getOutput()).doesNotContain("junit-platform-launcher");
  }

  private String listTestRuntimeOnlyTask() {
    return "tasks.register(\"listRuntimeDeps\") {\n"
        + "    doLast {\n"
        + "        configurations.getByName(\"testRuntimeOnly\").dependencies.forEach {\n"
        + "            println(\"RUNTIME_DEP: \" + it.group + \":\" + it.name + \":\" +"
        + " it.version)\n"
        + "        }\n"
        + "        configurations.getByName(\"testRuntimeOnly\").dependencyConstraints.forEach {\n"
        + "            println(\"RUNTIME_FLOOR: \" + it.group + \":\" + it.name + \":\" +"
        + " it.version)\n"
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

  // --- narrativetraceFeedback -----------------------------------------------------------------

  /**
   * The options every feedback run needs, with text no value-free rule refuses, and whichever of
   * them a case REPLACES. Gradle refuses a repeated command-line option, so an override has to
   * replace rather than append — which is what the map is for.
   */
  private static String[] feedbackArgs(String channel, String... overrides) {
    var options = new java.util.LinkedHashMap<String, String>();
    options.put("--channel", channel);
    options.put("--category", "library");
    options.put("--step", "the install block");
    options.put("--did", "applied the plugin and ran the build");
    options.put("--happened", "nothing appeared under the output directory");
    options.put("--expected", "one trace file per scenario");
    for (int i = 0; i < overrides.length; i += 2) {
      options.put(overrides[i], overrides[i + 1]);
    }
    var args = new java.util.ArrayList<String>(List.of("narrativetraceFeedback"));
    options.forEach(
        (option, value) -> {
          args.add(option);
          args.add(value);
        });
    return args.toArray(String[]::new);
  }

  @Test
  void narrativetraceFeedbackTaskIsRegistered(@TempDir Path projectDir) throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, "tasks", "--group", "help");

    assertThat(result.getOutput()).contains("narrativetraceFeedback");
  }

  @Test
  void narrativetraceFeedbackWritesBothFilesAndPrintsTheWholeDraft(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, feedbackArgs("draft"));

    assertThat(result.task(":narrativetraceFeedback").getOutcome()).isEqualTo(SUCCESS);
    var draft = projectDir.resolve("build/narrativetrace/feedback/feedback-draft.md");
    var body = projectDir.resolve("build/narrativetrace/feedback/feedback-body.md");
    assertThat(draft).exists();
    assertThat(body).exists();
    assertThat(Files.readString(draft)).contains(Files.readString(body));
    assertThat(result.getOutput())
        .contains("## What I did")
        .contains("nothing appeared under the output directory")
        .contains("Filing on GitHub is public");
  }

  @Test
  void narrativetraceFeedbackPrintsThePreFilledUrlOnTheUrlChannel(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradle(projectDir, feedbackArgs("url"));

    assertThat(result.getOutput())
        .contains("https://github.com/narrativetrace/narrativetrace-java/issues/new?")
        .contains("template=narrativetrace-report.yml")
        .contains("category=library");
  }

  @Test
  void narrativetraceFeedbackRefusesAReportCarryingAValueAndWritesNothing(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");

    var result =
        runGradleAndFail(
            projectDir,
            feedbackArgs(
                "draft", "--happened", "it rendered OrderService.placeOrder(customerId: \"C-1\")"));

    assertThat(result.getOutput())
        .contains("happened: vf.rendered-call")
        .contains("Nothing was written");
    assertThat(projectDir.resolve("build/narrativetrace/feedback")).doesNotExist();
  }

  @Test
  void narrativetraceFeedbackHonoursACustomOutputDir(@TempDir Path projectDir) throws IOException {
    writeBuildFile(
        projectDir, "narrativeTrace {\n    outputDir.set(layout.buildDirectory.dir(\"nt\"))\n}\n");

    runGradle(projectDir, feedbackArgs("draft"));

    assertThat(projectDir.resolve("build/nt/feedback/feedback-body.md")).exists();
  }

  @Test
  void narrativetraceFeedbackAttachesTheDoctorsReportWhenThisProjectHasOne(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");
    runGradle(projectDir, "narrativetraceDoctor");

    var result = runGradle(projectDir, feedbackArgs("draft", "--category", "doctor"));

    assertThat(result.task(":narrativetraceFeedback").getOutcome()).isEqualTo(SUCCESS);
    assertThat(
            Files.readString(projectDir.resolve("build/narrativetrace/feedback/feedback-body.md")))
        .contains("\"findings\"")
        .doesNotContain("No doctor report");
  }

  @Test
  void aDoctorCategoryReportWithoutTheDoctorsReportFailsAndSaysWhatToDo(@TempDir Path projectDir)
      throws IOException {
    writeBuildFile(projectDir, "");

    var result = runGradleAndFail(projectDir, feedbackArgs("draft", "--category", "doctor"));

    assertThat(result.getOutput())
        .contains("needs the doctor's JSON report")
        .contains("file this under prompt or library instead");
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

  // --- narrativetraceInit / narrativetraceUninstall / narrativetraceRefreshSkills --------------

  /** The version every family artifact resolves at, and so the version of the carrier. */
  private static final String CARRIER_VERSION =
      System.getProperty("narrativetrace.test.publishedVersion");

  private static final Path REPOSITORY_ROOT = Path.of(System.getProperty("projectDir"));

  private static final String DOCTOR_PAGE = ".agents/skills/narrativetrace-doctor/SKILL.md";

  private static final String STALE_COORDINATE = "ai.narrativetrace:narrativetrace-skills:0.0.1";

  @Test
  void theInstallerTasksAreRegisteredInTheirOwnGroup(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "tasks", "--group", "narrativetrace");

    assertThat(result.getOutput())
        .contains("narrativetraceInit")
        .contains("narrativetraceUninstall");
  }

  @Test
  void narrativetraceInitWritesTheSkillsAndTheStampedSection(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceInit");

    assertThat(result.task(":narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
    assertThat(Files.readString(projectDir.resolve(DOCTOR_PAGE)))
        .contains("installed by narrativetrace init from ai.narrativetrace:narrativetrace-skills:")
        .contains(CARRIER_VERSION);
    assertThat(Files.readString(projectDir.resolve("AGENTS.md")))
        .contains(
            "<!-- narrativetrace:start ai.narrativetrace:narrativetrace-skills:"
                + CARRIER_VERSION
                + " -->")
        .contains("<!-- narrativetrace:end -->");
  }

  @Test
  void narrativetraceInitDiffPrintsThePlanAndWritesNothing(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceInit", "--diff");

    assertThat(result.getOutput()).contains("+++ b/AGENTS.md").contains("@@");
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
    assertThat(projectDir.resolve(".agents")).doesNotExist();
  }

  @Test
  void aSecondNarrativetraceInitHasNothingToDo(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    runGradle(projectDir, "narrativetraceInit");

    var result = runGradle(projectDir, "narrativetraceInit");

    assertThat(result.getOutput()).contains("0 applied, 0 refused");
  }

  @Test
  void narrativetraceInitOnlySkillsLeavesTheSectionAlone(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    runGradle(projectDir, "narrativetraceInit", "--only", "skills");

    assertThat(projectDir.resolve(DOCTOR_PAGE)).exists();
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void narrativetraceInitOnlyAgentsMdLeavesTheSkillsAlone(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    runGradle(projectDir, "narrativetraceInit", "--only", "agents-md");

    assertThat(projectDir.resolve(".agents")).doesNotExist();
    assertThat(projectDir.resolve("AGENTS.md")).exists();
  }

  /**
   * A carrier nothing provides fails {@code narrativetraceInit} — the task a person explicitly ran
   * — with the coordinate named. The refresh task reads the same empty resolution and only warns,
   * which is the whole reason the artifact view is lenient rather than strict.
   */
  @Test
  void narrativetraceInitFailsWithTheCoordinateWhenNothingProvidesTheCarrier(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), "");

    var result = runGradleAndFail(projectDir, "narrativetraceInit");

    assertThat(result.getOutput())
        .contains("could not resolve the NarrativeTrace skills carrier")
        .contains("no repository in this build provides ai.narrativetrace:narrativetrace-skills");
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void anUnknownOnlyValueFailsTheTaskAndNamesWhatIsAccepted(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradleAndFail(projectDir, "narrativetraceInit", "--only", "everything");

    assertThat(result.getOutput()).contains("Invalid --only 'everything'").contains("agents-md");
  }

  @Test
  void narrativetraceInitJsonPrintsTheSameEnvelopeTheDoctorDoes(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceInit", "--json");

    assertThat(result.getOutput())
        .contains("\"carrier\": \"ai.narrativetrace:narrativetrace-skills:" + CARRIER_VERSION)
        .contains("\"status\": \"applied\"")
        .contains("\"exitCode\": 0");
  }

  /**
   * D12 at the Gradle surface: a person is at the keyboard, so a refusal is red and names the flag
   * that would allow it — never a log line under a green build.
   */
  @Test
  void aRefusalFailsTheTaskAndNamesTheFlagThatWouldAllowIt(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    Files.writeString(projectDir.resolve("AGENTS.md"), "# Mine\n");

    var result = runGradleAndFail(projectDir, "narrativetraceInit");

    assertThat(result.getOutput())
        .contains("narrativetraceInit refused to change this project")
        .contains("--write-existing");
    assertThat(Files.readString(projectDir.resolve("AGENTS.md"))).isEqualTo("# Mine\n");
  }

  @Test
  void aProjectWithAClaudeMdGetsTheVendorCopyAndTheImportLineOnlyWithTheFlag(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    Path repository = carrierRepository(repo);
    writeCarrierBuildFile(projectDir, repository, "");
    Files.writeString(projectDir.resolve("CLAUDE.md"), "# Claude\n");

    runGradleAndFail(projectDir, "narrativetraceInit");

    assertThat(projectDir.resolve(".claude/skills/narrativetrace-doctor/SKILL.md")).exists();
    assertThat(Files.readString(projectDir.resolve("CLAUDE.md"))).isEqualTo("# Claude\n");

    runGradle(projectDir, "narrativetraceInit", "--write-existing");

    assertThat(Files.readString(projectDir.resolve("CLAUDE.md"))).contains("@AGENTS.md");
  }

  @Test
  void narrativetraceUninstallLeavesTheTreeExactlyAsItWas(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    Files.writeString(projectDir.resolve("CLAUDE.md"), "# Claude\n");
    var before = tree(projectDir);

    runGradle(projectDir, "narrativetraceInit", "--write-existing");
    runGradle(projectDir, "narrativetraceUninstall");

    assertThat(tree(projectDir)).isEqualTo(before);
  }

  @Test
  void theInstallerTasksRunUnderTheConfigurationCache(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var first = runGradle(projectDir, "narrativetraceInit", "--diff", "--configuration-cache");
    var second = runGradle(projectDir, "narrativetraceInit", "--diff", "--configuration-cache");

    assertThat(first.getOutput()).doesNotContain("problems were found storing");
    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(second.task(":narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
  }

  /**
   * {@code --dry-run} is Gradle's own flag: it skips every task, so the obvious command for "show
   * me what this would do" prints nothing. The plugin says so while it is still being configured,
   * which is the last moment anything can.
   */
  @Test
  void gradlesOwnDryRunSaysWhyTheTaskDidNotRun(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");

    var result = runGradle(projectDir, "narrativetraceInit", "--dry-run");

    assertThat(result.getOutput())
        .contains("--dry-run is Gradle's own flag")
        .contains("./gradlew narrativetraceInit --diff");
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  /**
   * D5 at the Gradle surface: the tree {@code npx skills add} leaves is adopted by one plain {@code
   * narrativetraceInit}, no flag — and the link it put at the vendor path is REPLACED rather than
   * written through.
   *
   * <p>The discriminator is the vendor-only frontmatter key. A write that followed the link would
   * have landed the vendor flavour in the open-standard page, so asserting that each path holds its
   * OWN flavour is the same assertion as "nothing was written through the link", stated in bytes a
   * person can read.
   */
  @Test
  void aRegistryTreeIsAdoptedAndItsLinkReplacedRatherThanWrittenThrough(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), "");
    registryTree(projectDir);

    var result = runGradle(projectDir, "narrativetraceInit");

    assertThat(result.task(":narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
    assertThat(result.getOutput())
        .contains("adopted: identical to this carrier's page")
        .contains("replaces the symbolic link .claude/skills/narrativetrace-doctor");
    assertThat(Files.readString(projectDir.resolve(DOCTOR_PAGE)))
        .contains(CARRIER_VERSION)
        .doesNotContain(VENDOR_ONLY_FRONTMATTER);
    assertThat(Files.readString(projectDir.resolve(CLAUDE_DOCTOR_PAGE)))
        .contains(CARRIER_VERSION)
        .contains(VENDOR_ONLY_FRONTMATTER);
    assertThat(linksUnder(projectDir)).as("every link was replaced, none followed").isEmpty();
    assertThat(Files.readString(projectDir.resolve(REGISTRY_LOCK_FILE))).isEqualTo(REGISTRY_LOCK);
  }

  // --- refresh on build -----------------------------------------------------------------------

  @Test
  void aProjectThatNeverRanInitIsNeverTouchedByABuild(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), COMPILE_WITHOUT_THE_FAMILY);

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput()).doesNotContain("NarrativeTrace:");
    assertThat(projectDir.resolve(".agents")).doesNotExist();
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void aSkillDirectorySomebodyElseOwnsIsNeverTouchedByABuild(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    Path theirs = projectDir.resolve(".agents/skills/narrativetrace-doctor/SKILL.md");
    Files.createDirectories(theirs.getParent());
    Files.writeString(theirs, "---\nname: narrativetrace-doctor\n---\n\nmine, thanks\n");

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput()).doesNotContain("NarrativeTrace:");
    assertThat(Files.readString(theirs))
        .isEqualTo("---\nname: narrativetrace-doctor\n---\n\nmine, thanks\n");
  }

  @Test
  void anUpToDateInstallIsLeftAloneAndSaysNothing(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    runGradle(projectDir, "narrativetraceInit");
    var installed = tree(projectDir);

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput()).doesNotContain("NarrativeTrace: refreshed");
    assertThat(tree(projectDir)).isEqualTo(installed);
  }

  @Test
  void aStaleInstallIsRefreshedByTheBuildWithOneLine(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    writeStaleSkill(projectDir);

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput())
        .contains(
            "NarrativeTrace: refreshed 1 file to ai.narrativetrace:narrativetrace-skills:"
                + CARRIER_VERSION)
        .contains(DOCTOR_PAGE);
    assertThat(Files.readString(projectDir.resolve(DOCTOR_PAGE)))
        .doesNotContain(STALE_COORDINATE)
        .contains(CARRIER_VERSION);
  }

  /** A build never creates what init did not: a stale page is refreshed, AGENTS.md is not born. */
  @Test
  void aRefreshNeverStartsAnInstallOfItsOwn(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    writeStaleSkill(projectDir);

    runGradle(projectDir, "classes");

    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
    assertThat(projectDir.resolve(".claude")).doesNotExist();
  }

  @Test
  void aCarrierThatWillNotResolveWarnsOnceAndChangesNothing(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    writeStaleSkill(projectDir);
    var stale = tree(projectDir);

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput())
        .contains("could not check whether this project's agent skills are up to date")
        .contains("no repository in this build provides ai.narrativetrace:narrativetrace-skills");
    assertThat(tree(projectDir)).isEqualTo(stale);
  }

  /**
   * The refresh task sits on the {@code classes} path, so it is in EVERY consumer build. A
   * configuration-cache problem there would not cost a person one command, it would cost them their
   * build — which is why this is asserted on {@code classes} and not only on the task a person
   * types.
   */
  @Test
  void aBuildThatRefreshesStaysConfigurationCacheable(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    writeStaleSkill(projectDir);

    var first = runGradle(projectDir, "classes", "--configuration-cache");
    var second = runGradle(projectDir, "classes", "--configuration-cache");

    assertThat(first.getOutput())
        .doesNotContain("problems were found storing")
        .contains("NarrativeTrace: refreshed 1 file to ");
    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(Files.readString(projectDir.resolve(DOCTOR_PAGE))).contains(CARRIER_VERSION);
  }

  @Test
  void aProjectThatNeverRanInitStaysConfigurationCacheableToo(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, emptyRepository(repo), COMPILE_WITHOUT_THE_FAMILY);

    var first = runGradle(projectDir, "classes", "--configuration-cache");
    var second = runGradle(projectDir, "classes", "--configuration-cache");

    assertThat(first.getOutput()).doesNotContain("problems were found storing");
    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(projectDir.resolve(".agents")).doesNotExist();
  }

  /**
   * A build refreshes what {@code init} wrote and adopts NOTHING. The tree here is part registry
   * and part one-release-stale install on purpose: a build that refreshed nothing at all would also
   * pass a test where there was nothing to refresh, so the stale page of ours has to be rewritten
   * in the same run that leaves the registry's unstamped pages and its links untouched.
   */
  @Test
  void aBuildRefreshesOnlyWhatIsAlreadyOursAndAdoptsNoRegistryPage(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeCarrierBuildFile(projectDir, carrierRepository(repo), COMPILE_WITHOUT_THE_FAMILY);
    registryTree(projectDir);
    writeStaleSkill(projectDir);
    var theirPages = pagesNoInstallOfOursOwns(projectDir);
    var theirLinks = linksUnder(projectDir);

    var result = runGradle(projectDir, "classes");

    assertThat(result.getOutput())
        .contains(
            "NarrativeTrace: refreshed 1 file to ai.narrativetrace:narrativetrace-skills:"
                + CARRIER_VERSION)
        .contains(DOCTOR_PAGE);
    assertThat(Files.readString(projectDir.resolve(DOCTOR_PAGE)))
        .doesNotContain(STALE_COORDINATE)
        .contains(CARRIER_VERSION);
    assertThat(pagesNoInstallOfOursOwns(projectDir))
        .as("a build adopts no page a registry left")
        .isEqualTo(theirPages);
    assertThat(linksUnder(projectDir))
        .as("a build replaces no link, however adoptable what it reaches is")
        .isEqualTo(theirLinks);
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  // --- fixtures -------------------------------------------------------------------------------

  private static final String CLAUDE_DOCTOR_PAGE = ".claude/skills/narrativetrace-doctor/SKILL.md";

  /**
   * A frontmatter key only the VENDOR flavour carries, so "which flavour is this page" is one
   * {@code contains}. If the carrier ever renders the two flavours identically this stops
   * discriminating, and the registry-tree tests say nothing — which is why it is named here once
   * rather than spelled inline at each assertion.
   */
  private static final String VENDOR_ONLY_FRONTMATTER = "allowed-tools:";

  /** Anchored at column 0 in a stamped page, and absent from every page the carrier renders. */
  private static final String PROVENANCE_PREFIX = "<!-- installed by narrativetrace init from ";

  /** What {@code npx skills add} writes at the project root; nothing of ours may touch it. */
  private static final String REGISTRY_LOCK_FILE = "skills-lock.json";

  private static final String REGISTRY_LOCK = "{\"skills\": []}\n";

  /**
   * The tree {@code npx skills add} leaves behind: this release's own pages under the open-standard
   * root with NO provenance line, the vendor path a relative link to each one, and the registry's
   * own lock file at the project root.
   *
   * <p><b>@llmNote</b> Built by installing and then un-stamping, rather than from checked-in
   * fixtures. Adoption's whole premise is that the pages are byte-identical to what this carrier
   * renders, so a fixture copy would quietly stop testing adoption the first time a skill page is
   * edited — it would be testing a refusal instead, and passing.
   */
  private void registryTree(Path projectDir) throws IOException {
    runGradle(projectDir, "narrativetraceInit", "--only", "skills");
    for (Path skill : skillDirectories(projectDir)) {
      unstamp(skill.resolve("SKILL.md"));
      String name = skill.getFileName().toString();
      Path vendor = projectDir.resolve(".claude/skills").resolve(name);
      Files.createDirectories(vendor.getParent());
      Files.createSymbolicLink(vendor, Path.of("../../.agents/skills", name));
    }
    Files.writeString(projectDir.resolve(REGISTRY_LOCK_FILE), REGISTRY_LOCK);
  }

  private static java.util.List<Path> skillDirectories(Path projectDir) throws IOException {
    try (var children = Files.list(projectDir.resolve(".agents/skills"))) {
      return children.toList();
    }
  }

  /** The page as a registry checked it out: ours, minus the one line that says so. */
  private static void unstamp(Path page) throws IOException {
    String stamped = Files.readString(page);
    int start = stamped.indexOf(PROVENANCE_PREFIX);
    assertThat(start).as("%s carries a provenance line to remove", page).isNotNegative();
    Files.writeString(
        page, stamped.substring(0, start) + stamped.substring(stamped.indexOf('\n', start) + 1));
  }

  /**
   * The pages in the open-standard root that no install of ours owns — in a part-and-part tree,
   * exactly the ones a registry left. The doctor's page is the one {@link #writeStaleSkill} makes
   * ours, so it is what this excludes.
   */
  private static java.util.Map<String, String> pagesNoInstallOfOursOwns(Path projectDir)
      throws IOException {
    var pages = tree(projectDir);
    pages.keySet().removeIf(path -> !path.startsWith(".agents") || path.contains("-doctor"));
    return pages;
  }

  /**
   * Every symbolic link under the project, by relative path. Listed, never followed: {@link
   * Files#walk} does not descend through one, so a link's own target tree contributes nothing here.
   */
  private static java.util.List<String> linksUnder(Path projectDir) throws IOException {
    try (var walk = Files.walk(projectDir)) {
      return walk.filter(Files::isSymbolicLink)
          .map(link -> projectDir.relativize(link).toString())
          .sorted()
          .toList();
    }
  }

  /**
   * The family's own artifacts are not in the one-jar carrier repository these tests use, and
   * {@code classes} resolves the compile classpath. Excluding them there keeps the plugin ENABLED —
   * which is the state the refresh task has to behave correctly in — without asking a nested build
   * to reach a network.
   */
  private static final String COMPILE_WITHOUT_THE_FAMILY =
      "configurations.named(\"compileClasspath\") { exclude(group = \"ai.narrativetrace\") }\n";

  /** The real carrier jar this build produced, laid out as a local Maven file repository. */
  private Path carrierRepository(Path repo) throws IOException {
    Path target = repo.resolve("ai/narrativetrace/narrativetrace-skills/" + CARRIER_VERSION);
    Files.createDirectories(target);
    Files.copy(
        REPOSITORY_ROOT.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + CARRIER_VERSION + ".jar"),
        target.resolve("narrativetrace-skills-" + CARRIER_VERSION + ".jar"));
    Files.writeString(
        target.resolve("narrativetrace-skills-" + CARRIER_VERSION + ".pom"), carrierPom());
    return repo;
  }

  /** A repository that exists and carries nothing — what being offline looks like to Gradle. */
  private Path emptyRepository(Path repo) throws IOException {
    Files.createDirectories(repo);
    return repo;
  }

  private static String carrierPom() {
    return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>ai.narrativetrace</groupId>\n"
        + "  <artifactId>narrativetrace-skills</artifactId>\n"
        + "  <version>"
        + CARRIER_VERSION
        + "</version>\n"
        + "  <packaging>jar</packaging>\n"
        + "</project>\n";
  }

  /** A page of ours from an older carrier — what an install looks like one release later. */
  private void writeStaleSkill(Path projectDir) throws IOException {
    Path page = projectDir.resolve(DOCTOR_PAGE);
    Files.createDirectories(page.getParent());
    Files.writeString(
        page,
        "---\nname: narrativetrace-doctor\n---\n\n<!-- installed by narrativetrace init from "
            + STALE_COORDINATE
            + " — edit the catalogue, not this file -->\n\nolder words\n");
  }

  private void writeCarrierBuildFile(Path projectDir, Path repo, String extraConfig)
      throws IOException {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-project\"");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"ai.narrativetrace\")\n"
            + "}\n"
            + "repositories { maven { url = uri(\""
            + repo.toUri()
            + "\") } }\n"
            + extraConfig);
  }

  /** Every file under the project, by relative path — the comparison an uninstall has to pass. */
  private static java.util.Map<String, String> tree(Path projectDir) throws IOException {
    var files = new java.util.TreeMap<String, String>();
    try (var walk = Files.walk(projectDir)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        String relative = projectDir.relativize(file).toString();
        if (!relative.startsWith("build") && !relative.startsWith(".gradle")) {
          files.put(relative, Files.readString(file));
        }
      }
    }
    return files;
  }

  private org.gradle.testkit.runner.BuildResult runGradleAndFail(Path projectDir, String... args) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .buildAndFail();
  }
}
