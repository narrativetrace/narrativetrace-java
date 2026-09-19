/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every input that determines a generated output is declared — proved as behaviour, per task: first
 * run executes, an unchanged rerun is UP-TO-DATE, and touching the input reruns it.
 *
 * <p>INTENT (build-automation assessment 2026-09-14, Priority 1): {@code jdepend} declared its JSON
 * output but not the class files it analyses (a {@code dependsOn(classes)} orders execution and
 * fingerprints nothing); {@code dependencyReport} declared an output and no inputs at all, which
 * Gradle treats as up-to-date whenever the output is unchanged; {@code generateLlmsDocs} copied
 * {@code documentation/llms.txt} inside {@code doLast} without declaring it a source. Each could
 * hand an incremental build a stale report while looking green.
 *
 * <p>The typed {@code JDependReportTask} and {@code DependencyReportTask} are driven in throwaway
 * fixture builds (their inputs are compiled classes and dependency declarations, and editing a real
 * module mid-suite is not an option), with a real-tree run each for the root build's own wiring;
 * {@code generateLlmsDocs} runs against this repository, asking the build which sources it declares
 * and exercising reruns through its own output directory — never by editing a tracked page.
 */
class TaskInputInvalidationTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));

  /** buildSrc's classes and their runtime dependencies, for a fixture build to load them from. */
  private static final List<String> BUILD_SRC_CLASSPATH =
      Arrays.asList(System.getProperty("buildSrcClasspath").split(File.pathSeparator));

  private static BuildResult gradle(File projectDir, String... args) {
    var allArgs = new ArrayList<>(List.of(args));
    allArgs.add("-q");
    return GradleRunner.create().withProjectDir(projectDir).withArguments(allArgs).build();
  }

  private static TaskOutcome outcome(BuildResult result, String taskPath) {
    return result.task(taskPath).getOutcome();
  }

  // --- jdepend: the analysed classes are the input -------------------------

  @Test
  void jdependRerunsWhenAnAnalysedClassChangesAndOnlyThen(@TempDir Path fixture)
      throws IOException {
    writeJdependFixture(fixture);
    var json = fixture.resolve("build/reports/jdepend/jdepend.json");

    assertThat(outcome(gradle(fixture.toFile(), "jdepend"), ":jdepend")).isEqualTo(SUCCESS);
    assertThat(Files.readString(json))
        .contains("\"module\":\"fixture\"")
        .contains("\"ai.narrativetrace.fixture.a\"");

    assertThat(outcome(gradle(fixture.toFile(), "jdepend"), ":jdepend"))
        .as("nothing changed")
        .isEqualTo(UP_TO_DATE);

    Files.createDirectories(fixture.resolve("src/main/resources"));
    Files.writeString(fixture.resolve("src/main/resources/note.txt"), "not a class\n");
    // processResources is requested explicitly: jdepend depends on compileJava alone.
    var resourceOnly = gradle(fixture.toFile(), "processResources", "jdepend");
    assertThat(outcome(resourceOnly, ":processResources")).isEqualTo(SUCCESS);
    assertThat(outcome(resourceOnly, ":jdepend"))
        .as("a resource is not an analysed class — the input is the classes directory")
        .isEqualTo(UP_TO_DATE);

    var b = Files.createDirectories(fixture.resolve("src/main/java/ai/narrativetrace/fixture/b"));
    Files.writeString(
        b.resolve("B.java"), "package ai.narrativetrace.fixture.b;\npublic class B {}\n");
    assertThat(outcome(gradle(fixture.toFile(), "jdepend"), ":jdepend"))
        .as("a new class changes the analysed input")
        .isEqualTo(SUCCESS);
    assertThat(Files.readString(json)).contains("\"ai.narrativetrace.fixture.b\"");
  }

  /**
   * The root build's own wiring of the typed task: the report names the MODULE. Until 2026-09-14
   * every per-module report said {@code "module":"jdepend"} — the registration lambda's {@code
   * name} was the task's, not the project's.
   */
  @Test
  void perModuleJdependReportNamesTheModuleAndIsUpToDateOnRerun() throws IOException {
    gradle(PROJECT_DIR, ":narrativetrace-slf4j:jdepend");
    var json = new File(PROJECT_DIR, "narrativetrace-slf4j/build/reports/jdepend/jdepend.json");
    assertThat(Files.readString(json.toPath())).contains("\"module\":\"narrativetrace-slf4j\"");

    var rerun = gradle(PROJECT_DIR, ":narrativetrace-slf4j:jdepend");
    assertThat(outcome(rerun, ":narrativetrace-slf4j:jdepend")).isEqualTo(UP_TO_DATE);
  }

  private static void writeJdependFixture(Path fixture) throws IOException {
    Files.writeString(fixture.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
    Files.writeString(
        fixture.resolve("build.gradle"),
        buildscriptClasspath()
            + "apply plugin: 'java'\n"
            + "tasks.register('jdepend', ai.narrativetrace.build.JDependReportTask) {\n"
            + "  moduleName = 'fixture'\n"
            + "  classesDir = tasks.named('compileJava').flatMap { it.destinationDirectory }\n"
            + "  jsonFile = layout.buildDirectory.file('reports/jdepend/jdepend.json')\n"
            + "}\n");
    // JDependReportSupport reports `ai.narrativetrace.*` packages only, by design.
    var a = Files.createDirectories(fixture.resolve("src/main/java/ai/narrativetrace/fixture/a"));
    Files.writeString(
        a.resolve("A.java"), "package ai.narrativetrace.fixture.a;\npublic class A {}\n");
  }

  private static String buildscriptClasspath() {
    return "buildscript { dependencies { classpath files("
        + BUILD_SRC_CLASSPATH.stream()
            .map(p -> "'" + p.replace("\\", "/") + "'")
            .collect(Collectors.joining(", "))
        + ") } }\n";
  }

  // --- dependencyReport: the declared graph is the input -------------------

  /**
   * Driven in a fixture rather than this repository: the only ways to change a declaration here are
   * editing a tracked build file or an init script, and an init script is not a fair probe — it
   * shifts the build-script classpath, and Gradle reruns any task with script-defined actions for
   * that reason alone ("additional actions ... have changed"), whatever its inputs say.
   */
  @Test
  void dependencyReportRerunsWhenADependencyDeclarationChangesAndOnlyThen(@TempDir Path fixture)
      throws IOException {
    writeDependencyReportFixture(fixture);
    var report = fixture.resolve("build/reports/dependency-graph/module-dependencies.txt");

    assertThat(outcome(gradle(fixture.toFile(), "dependencyReport"), ":dependencyReport"))
        .isEqualTo(SUCCESS);
    assertThat(Files.readString(report))
        .contains("## a\n  -> b\n  -> org.example:lib:1.0\n")
        .contains("## b\n  (no dependencies)\n");

    assertThat(outcome(gradle(fixture.toFile(), "dependencyReport"), ":dependencyReport"))
        .as("nothing changed")
        .isEqualTo(UP_TO_DATE);

    Files.writeString(
        fixture.resolve("b/build.gradle"),
        "// a comment is not a declaration\napply plugin: 'java'\n");
    assertThat(outcome(gradle(fixture.toFile(), "dependencyReport"), ":dependencyReport"))
        .as("a build-script edit that changes no declaration")
        .isEqualTo(UP_TO_DATE);

    Files.writeString(
        fixture.resolve("a/build.gradle"),
        "apply plugin: 'java'\n"
            + "dependencies { implementation project(':b'); implementation 'org.example:lib:2.0'"
            + " }\n");
    assertThat(outcome(gradle(fixture.toFile(), "dependencyReport"), ":dependencyReport"))
        .as("a changed declaration reruns the report")
        .isEqualTo(SUCCESS);
    assertThat(Files.readString(report)).contains("  -> org.example:lib:2.0\n");
  }

  /** The root build's own wiring: the real report is produced once and then reused. */
  @Test
  void dependencyReportIsUpToDateOnAnUnchangedRerun() {
    gradle(PROJECT_DIR, "dependencyReport");

    assertThat(outcome(gradle(PROJECT_DIR, "dependencyReport"), ":dependencyReport"))
        .isEqualTo(UP_TO_DATE);
  }

  private static void writeDependencyReportFixture(Path fixture) throws IOException {
    Files.writeString(
        fixture.resolve("settings.gradle"), "rootProject.name = 'fixture'\ninclude 'a', 'b'\n");
    Files.writeString(
        fixture.resolve("build.gradle"),
        buildscriptClasspath()
            + "tasks.register('dependencyReport', ai.narrativetrace.build.DependencyReportTask) {\n"
            + "  modules.set(provider {\n"
            // Groovy reaches a Kotlin `object`'s methods through INSTANCE.
            + "    subprojects.collect {"
            + " ai.narrativetrace.build.DependencyReportSupport.INSTANCE.collect(it) }\n"
            + "  })\n"
            + "  outputFile = layout.buildDirectory.file("
            + "'reports/dependency-graph/module-dependencies.txt')\n"
            + "}\n");
    Files.createDirectories(fixture.resolve("a"));
    Files.createDirectories(fixture.resolve("b"));
    Files.writeString(
        fixture.resolve("a/build.gradle"),
        "apply plugin: 'java'\n"
            + "dependencies { implementation project(':b'); implementation 'org.example:lib:1.0'"
            + " }\n");
    Files.writeString(fixture.resolve("b/build.gradle"), "apply plugin: 'java'\n");
  }

  // --- generateLlmsDocs: llms.txt is a declared source ---------------------

  /**
   * The regression is that a source page is READ inside {@code doLast} instead of DECLARED, so the
   * build's own answer to "which files are your sources" is the proof: a declared input is what
   * Gradle fingerprints, and an undeclared one is what left the generated copy stale while the task
   * reported UP-TO-DATE.
   */
  @Test
  void generateLlmsDocsDeclaresBothSourcePagesAsInputs() {
    var declared = gradle(PROJECT_DIR, "printLlmsDocsSources").getOutput();

    assertThat(declared)
        .as("a source read inside doLast is not an input — Gradle fingerprints what is declared")
        .contains("documentation/llms.txt")
        .contains("documentation/llms-full.md");
  }

  /**
   * The behavioural half, kept to what a test may touch: {@code build/site} is this task's own
   * output. Asking the question by editing {@code documentation/llms.txt} and restoring it
   * afterwards — what this test did until 2026-09-18 — writes where the repository lives: invisible
   * while it passes, a rewritten tracked page on any run that never reaches the restore.
   */
  @Test
  void generateLlmsDocsRerunsWhenItsOutputIsMissingAndOnlyThen() throws IOException {
    var source = new File(PROJECT_DIR, "documentation/llms.txt").toPath();
    var copy = new File(PROJECT_DIR, "build/site/llms.txt").toPath();

    gradle(PROJECT_DIR, "generateLlmsDocs");
    assertThat(Files.readAllBytes(copy)).isEqualTo(Files.readAllBytes(source));
    assertThat(outcome(gradle(PROJECT_DIR, "generateLlmsDocs"), ":generateLlmsDocs"))
        .as("nothing changed")
        .isEqualTo(UP_TO_DATE);

    Files.delete(copy);

    assertThat(outcome(gradle(PROJECT_DIR, "generateLlmsDocs"), ":generateLlmsDocs"))
        .as("a missing output reruns the copy")
        .isEqualTo(SUCCESS);
    assertThat(Files.readAllBytes(copy)).isEqualTo(Files.readAllBytes(source));
  }
}
