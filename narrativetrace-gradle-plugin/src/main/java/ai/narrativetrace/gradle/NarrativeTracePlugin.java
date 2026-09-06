/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

/**
 * Gradle plugin that auto-configures NarrativeTrace for Java projects.
 *
 * <p>INTENT: Apply this plugin to Java projects that should automatically get NarrativeTrace
 * dependencies, compiler flags, test configuration, and clarity tasks.
 *
 * <p><b>@llmNote</b> Task registration and compiler flags are configured lazily via {@code
 * withId("java",...)}. Dependency resolution, validation, and test task system properties remain in
 * {@code afterEvaluate} because they read eagerly-evaluated extension properties.
 */
public class NarrativeTracePlugin implements Plugin<Project> {

  private static final Set<String> VALID_MODES = Set.of("proxy", "agent", "spring");
  private static final Set<String> VALID_TEST_FRAMEWORKS = Set.of("junit4", "junit5");
  private static final Set<String> VALID_SCOPES = Set.of("test", "production");
  private static final Set<String> VALID_FORMATS =
      Set.of("markdown", "text", "mermaid", "plantuml");
  private static final Set<String> VALID_LEVELS =
      Set.of("OFF", "ERRORS", "SUMMARY", "NARRATIVE", "DETAIL");

  @Override
  public void apply(Project project) {
    var extension = project.getExtensions().create("narrativeTrace", NarrativeTraceExtension.class);
    extension
        .getOutputDir()
        .convention(project.getLayout().getBuildDirectory().dir("narrativetrace"));
    extension
        .getApprovedDir()
        .convention(project.getLayout().getProjectDirectory().dir("src/test/narratives"));

    project
        .getPlugins()
        .withId(
            "java",
            javaPlugin -> {
              configureCompilerFlags(project);
              registerClarityCheckTask(project, extension);
              registerClarityScanTask(project, extension);
              registerGlossaryScanTask(project, extension);
              registerApproveNarrativesTask(project, extension);
            });

    project.afterEvaluate(
        p -> {
          if (!extension.getEnabled().get()) {
            return;
          }
          EnvironmentCheck.verify(p.getGradle().getGradleVersion(), javaMajorOf(p));
          validateExtension(p, extension);
          p.getPlugins()
              .withId(
                  "java",
                  javaPlugin -> {
                    var version =
                        extension.getLibraryVersion().getOrElse(VersionResolver.resolve());
                    configureDependencies(p, extension, version);
                    configureTestTasks(p, extension, version);
                  });
        });
  }

  /**
   * The Java feature release this build compiles with: the configured toolchain when there is one,
   * otherwise the JVM running Gradle. {@link EnvironmentCheck#UNKNOWN_JAVA} when neither can be
   * read — an unreadable version never fails the build.
   */
  private static int javaMajorOf(Project project) {
    var java = project.getExtensions().findByType(JavaPluginExtension.class);
    if (java != null) {
      var configured = java.getToolchain().getLanguageVersion().getOrNull();
      if (configured != null) {
        return configured.asInt();
      }
    }
    try {
      return Integer.parseInt(JavaVersion.current().getMajorVersion());
    } catch (NumberFormatException e) {
      return EnvironmentCheck.UNKNOWN_JAVA;
    }
  }

  private void validateExtension(Project project, NarrativeTraceExtension extension) {
    var mode = extension.getMode().get();
    if (!VALID_MODES.contains(mode)) {
      throw new GradleException(
          "Invalid narrativeTrace mode '" + mode + "'. Valid values: proxy, agent, spring");
    }
    var testFramework = extension.getTestFramework().get();
    if (!VALID_TEST_FRAMEWORKS.contains(testFramework)) {
      throw new GradleException(
          "Invalid narrativeTrace testFramework '"
              + testFramework
              + "'. Valid values: junit4, junit5");
    }
    var scope = extension.getScope().get();
    if (!VALID_SCOPES.contains(scope)) {
      throw new GradleException(
          "Invalid narrativeTrace scope '" + scope + "'. Valid values: test, production");
    }
    if (extension.getFormat().isPresent()) {
      var format = extension.getFormat().get();
      if (!VALID_FORMATS.contains(format)) {
        throw new GradleException(
            "Invalid narrativeTrace format '"
                + format
                + "'. Valid values: markdown, text, mermaid, plantuml");
      }
    }
    if (extension.getTracingLevel().isPresent()) {
      var level = extension.getTracingLevel().get();
      if (!VALID_LEVELS.contains(level)) {
        throw new GradleException(
            "Invalid narrativeTrace tracingLevel '"
                + level
                + "'. Valid values: OFF, ERRORS, SUMMARY, NARRATIVE, DETAIL");
      }
    }
    if (extension.getModules().getSpringWeb().get() && !"spring".equals(mode)) {
      project.getLogger().warn("NarrativeTrace: springWeb is enabled but mode is not 'spring'.");
    }
  }

  private void configureCompilerFlags(Project project) {
    project
        .getTasks()
        .withType(JavaCompile.class)
        .configureEach(task -> task.getOptions().getCompilerArgs().add("-parameters"));
  }

  private void configureDependencies(
      Project project, NarrativeTraceExtension extension, String version) {
    var modules = extension.getModules();
    var resolved =
        DependencyConfigurator.resolve(
            extension.getMode().get(),
            extension.getTestFramework().get(),
            extension.getScope().get(),
            modules.getSlf4j().get(),
            modules.getMicrometer().get(),
            modules.getServlet().get(),
            modules.getSpringWeb().get(),
            modules.getOpentelemetry().get(),
            modules.getMicronaut().get(),
            modules.getMicronautHttp().get(),
            version);

    var deps = project.getDependencies();
    for (var dep : resolved) {
      deps.add(dep.configuration(), dep.artifact());
    }
  }

  private void registerClarityCheckTask(Project project, NarrativeTraceExtension extension) {
    var clarityCheck =
        project
            .getTasks()
            .register(
                "clarityCheck",
                ClarityCheckTask.class,
                task -> {
                  task.setDescription("Checks clarity scores against configured thresholds");
                  task.setGroup("verification");
                  task.onlyIf(t -> extension.getEnabled().get());
                  var jsonFile = extension.getOutputDir().file("clarity-results.json");
                  task.getJsonFile().set(jsonFile);
                  task.getInputs()
                      .files(jsonFile)
                      .withPropertyName("jsonFileInput")
                      .withPathSensitivity(PathSensitivity.RELATIVE)
                      .optional();
                  task.getStampFile()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file("narrativetrace/clarity-check.stamp"));
                  task.getMinScore().set(extension.getClarity().getMinScore());
                  task.getMaxHighIssues().set(extension.getClarity().getMaxHighIssues());
                  task.getMaxSuiteIssues().set(extension.getClarity().getMaxSuiteIssues());
                  task.getWarnOnly().set(extension.getClarity().getWarnOnly());
                  task.dependsOn(project.getTasks().named("test"));
                });

    project.getTasks().named("check").configure(check -> check.dependsOn(clarityCheck));
  }

  private void registerClarityScanTask(Project project, NarrativeTraceExtension extension) {
    project
        .getTasks()
        .register(
            "clarityScan",
            JavaExec.class,
            task -> {
              task.setDescription(
                  "Analyzes naming clarity of compiled classes without running tests");
              task.setGroup("verification");
              task.onlyIf(t -> extension.getEnabled().get());
              // The glossary module's entry point, not the clarity module's: reading the committed
              // glossary needs a module that depends on clarity, so the scan scores in the
              // project's own vocabulary only through this class. It behaves identically when the
              // repository has no glossary.json.
              task.getMainClass().set("ai.narrativetrace.glossary.GlossaryAwareClarityScannerMain");

              var classesDir = project.getLayout().getBuildDirectory().dir("classes/java/main");
              var outputDir = extension.getOutputDir();
              task.doFirst(
                  "resolveClarityScanArgs",
                  t ->
                      ((JavaExec) t)
                          .args(
                              ClarityScanArguments.forScan(
                                  classesDir.get().getAsFile(),
                                  outputDir.get().getAsFile(),
                                  project.getRootDir())));

              task.classpath(project.getConfigurations().getByName("testRuntimeClasspath"));
              task.dependsOn(project.getTasks().named("classes"));
            });
  }

  private void registerGlossaryScanTask(Project project, NarrativeTraceExtension extension) {
    project
        .getTasks()
        .register(
            "glossaryScan",
            JavaExec.class,
            task -> {
              task.setDescription(
                  "Harvests the domain glossary from compiled classes without running tests");
              task.setGroup("verification");
              task.onlyIf(t -> extension.getEnabled().get());
              task.getMainClass().set("ai.narrativetrace.glossary.GlossaryScannerMain");

              var classesDir = project.getLayout().getBuildDirectory().dir("classes/java/main");
              var outputDir = extension.getOutputDir();
              task.doFirst(
                  "announceGlossaryScanWrites",
                  t ->
                      t.getLogger()
                          .lifecycle(GlossaryScanAnnouncement.forRoot(project.getRootDir())));
              task.doFirst(
                  "resolveGlossaryScanArgs",
                  t ->
                      ((JavaExec) t)
                          .args(
                              "--classes-dir",
                              classesDir.get().getAsFile().getAbsolutePath(),
                              "--glossary-dir",
                              project.getRootDir().getAbsolutePath(),
                              "--output-dir",
                              outputDir.get().getAsFile().getAbsolutePath()));

              task.classpath(project.getConfigurations().getByName("testRuntimeClasspath"));
              task.dependsOn(project.getTasks().named("classes"));
            });
  }

  /**
   * Registers {@code approveNarratives}: promotes every reviewed {@code *.received.nt} under the
   * configured narratives directory to its {@code *.approved.nt} baseline. Always safe to run —
   * nothing to promote is a no-op, reported per file when it does promote.
   */
  private void registerApproveNarrativesTask(Project project, NarrativeTraceExtension extension) {
    project
        .getTasks()
        .register(
            "approveNarratives",
            task -> {
              task.setDescription(
                  "Promotes reviewed *.received.nt narratives to *.approved.nt baselines");
              task.setGroup("verification");
              task.doLast(
                  t -> {
                    try {
                      var promoted =
                          ReceivedNarrativesSweep.promote(
                              extension.getApprovedDir().get().getAsFile().toPath());
                      if (promoted.isEmpty()) {
                        t.getLogger().lifecycle("No received narratives to approve.");
                      } else {
                        promoted.forEach(p -> t.getLogger().lifecycle("Approved: " + p));
                      }
                    } catch (java.io.IOException e) {
                      throw new GradleException(
                          "approveNarratives could not promote received narratives: "
                              + e.getMessage(),
                          e);
                    }
                  });
            });
  }

  private void configureTestTasks(
      Project project, NarrativeTraceExtension extension, String version) {
    project
        .getTasks()
        .withType(
            Test.class,
            task -> {
              if ("junit5".equals(extension.getTestFramework().get())) {
                task.useJUnitPlatform();
              }
              task.systemProperty("narrativetrace.output", "true");
              task.systemProperty(
                  "narrativetrace.outputDir",
                  extension.getOutputDir().get().getAsFile().getAbsolutePath());
              if (extension.getFormat().isPresent()) {
                task.systemProperty("narrativetrace.format", extension.getFormat().get());
              }
              if (extension.getTracingLevel().isPresent()) {
                task.systemProperty("narrativetrace.level", extension.getTracingLevel().get());
              }
              if (Boolean.TRUE.equals(extension.getGlossary().get())) {
                task.systemProperty("narrativetrace.glossary", "true");
                task.systemProperty(
                    "narrativetrace.glossaryDir", project.getRootDir().getAbsolutePath());
              }
              if (Boolean.TRUE.equals(extension.getApproval().get())) {
                task.systemProperty("narrativetrace.approval", "true");
                task.systemProperty(
                    "narrativetrace.approvedDir",
                    extension.getApprovedDir().get().getAsFile().getAbsolutePath());
              }
            });

    if ("agent".equals(extension.getMode().get())) {
      configureAgentJvmArg(project, extension, version);
    }
  }

  private void configureAgentJvmArg(
      Project project, NarrativeTraceExtension extension, String version) {
    var agentConfig =
        project
            .getConfigurations()
            .create(
                "narrativeTraceAgent",
                c -> {
                  c.setDescription("NarrativeTrace agent JAR for -javaagent");
                  c.setVisible(false);
                  c.setTransitive(false);
                });
    project
        .getDependencies()
        .add("narrativeTraceAgent", "ai.narrativetrace:narrativetrace-agent:" + version);

    var packages = extension.getAgent().getPackages().get();

    project
        .getTasks()
        .withType(
            Test.class,
            task ->
                task.doFirst(
                    "resolveNarrativeTraceAgent",
                    t -> {
                      var agentJar = agentConfig.getSingleFile();
                      ((Test) t)
                          .jvmArgs(
                              AgentJvmArgFactory.javaagentArg(
                                  agentJar.getAbsolutePath(), packages));
                    }));
  }
}
