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
import java.util.TreeMap;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-341: the agent skills belong once at the repository root, wherever the plugin is applied — on
 * an aggregator root that has no Java plugin, in the modules only, or in every project at once.
 */
class RootInstallFunctionalTest {

  private static final String CARRIER_VERSION =
      System.getProperty("narrativetrace.test.publishedVersion");

  private static final Path REPOSITORY_ROOT = Path.of(System.getProperty("projectDir"));

  private static final String SKIPPED = "already done in this build by";

  /** The aggregator root of the commonest layout: the plugin, no Java plugin, one Java module. */
  @Test
  void anAggregatorRootWithoutTheJavaPluginInstallsAtTheRootAndNotInTheModule(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeSettings(projectDir, repositoriesBlock(carrierRepository(repo)), "app");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"), "plugins { id(\"ai.narrativetrace\") }\n");
    writeJavaModule(projectDir, "app", true);

    var result = run(projectDir, "narrativetraceInit");

    assertThat(result.task(":narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
    assertThat(projectDir.resolve("AGENTS.md")).exists();
    assertThat(projectDir.resolve(".agents/skills")).isDirectory();
    assertThat(nothingNarrativeTraceWroteUnder(projectDir.resolve("app"))).isTrue();
  }

  /** The guide's multi-project layout: the plugin only in the subprojects that have tests. */
  @Test
  void aPluginAppliedOnlyInTheSubprojectInstallsAtTheRootNotInTheModule(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeSettings(projectDir, repositoriesBlock(carrierRepository(repo)), "app");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins { id(\"ai.narrativetrace\") apply false }\n");
    writeJavaModule(projectDir, "app", true);

    var result = run(projectDir, "narrativetraceInit");

    assertThat(result.task(":app:narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
    assertThat(projectDir.resolve("AGENTS.md")).exists();
    assertThat(projectDir.resolve(".agents/skills")).isDirectory();
    assertThat(nothingNarrativeTraceWroteUnder(projectDir.resolve("app"))).isTrue();
  }

  /** Every project applies the plugin and all of them share one target: one install per build. */
  @Test
  void everyProjectApplyingThePluginInstallsOnceUnderParallel(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeThreeProjectBuild(projectDir, repo);

    var result = run(projectDir, "narrativetraceInit", "--parallel", "--info");

    assertThat(result.getOutput().split(SKIPPED, -1).length - 1).isEqualTo(2);
    assertThat(projectDir.resolve("AGENTS.md")).exists();
    assertThat(nothingNarrativeTraceWroteUnder(projectDir.resolve("a"))).isTrue();
    assertThat(nothingNarrativeTraceWroteUnder(projectDir.resolve("b"))).isTrue();
  }

  @Test
  void theOncePerBuildRuleHoldsUnderTheConfigurationCache(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeThreeProjectBuild(projectDir, repo);

    var first = run(projectDir, "narrativetraceInit", "--configuration-cache", "--info");
    var second = run(projectDir, "narrativetraceInit", "--configuration-cache", "--info");

    assertThat(first.getOutput()).doesNotContain("problems were found storing");
    assertThat(first.getOutput().split(SKIPPED, -1).length - 1).isEqualTo(2);
    assertThat(second.getOutput()).contains("Reusing configuration cache.");
    assertThat(second.getOutput().split(SKIPPED, -1).length - 1).isEqualTo(2);
  }

  @Test
  void aDiffIsPrintedOnceNotOncePerProject(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeThreeProjectBuild(projectDir, repo);

    var result = run(projectDir, "narrativetraceInit", "--diff", "--info");

    assertThat(result.getOutput().split(SKIPPED, -1).length - 1).isEqualTo(2);
    assertThat(projectDir.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void uninstallFromTheRootRemovesTheRootInstall(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeThreeProjectBuild(projectDir, repo);
    var before = tree(projectDir);
    run(projectDir, "narrativetraceInit");
    assertThat(projectDir.resolve("AGENTS.md")).exists();

    run(projectDir, "narrativetraceUninstall", "--info");

    assertThat(tree(projectDir)).isEqualTo(before);
  }

  @Test
  void theDoctorOnAnAggregatorRootHasNoFailingFindingForTheMissingJavaPlugin(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeSettings(projectDir, repositoriesBlock(carrierRepository(repo)), "app");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"), "plugins { id(\"ai.narrativetrace\") }\n");
    writeJavaModule(projectDir, "app", true);

    run(projectDir, "narrativetraceInit");

    var result = run(projectDir, ":narrativetraceDoctor");

    assertThat(result.task(":narrativetraceDoctor").getOutcome()).isEqualTo(SUCCESS);
    assertThat(projectDir.resolve("build/narrativetrace/doctor-report.json")).exists();
  }

  @Test
  void aModulesDoctorSeesTheSkillsInstalledAtTheRoot(@TempDir Path projectDir, @TempDir Path repo)
      throws IOException {
    writeThreeProjectBuild(projectDir, repo);
    run(projectDir, "narrativetraceInit");

    var result = run(projectDir, ":a:narrativetraceDoctor");

    assertThat(result.task(":a:narrativetraceDoctor").getOutcome()).isEqualTo(SUCCESS);
    assertThat(projectDir.resolve("a/build/narrativetrace/doctor-report.json")).exists();
  }

  /** Repositories declared only in settings: the root declares none of its own. */
  @Test
  void repositoriesDeclaredOnlyInSettingsReachTheCarrier(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    writeSettings(projectDir, repositoriesBlock(carrierRepository(repo)), "app");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"), "plugins { id(\"ai.narrativetrace\") }\n");
    writeJavaModule(projectDir, "app", false);

    var result = run(projectDir, "narrativetraceInit");

    assertThat(result.task(":narrativetraceInit").getOutcome()).isEqualTo(SUCCESS);
    assertThat(projectDir.resolve("AGENTS.md")).exists();
  }

  @Test
  void noRepositoryProvidingTheCarrierFailsInitPlainlyOnAnAggregatorRoot(
      @TempDir Path projectDir, @TempDir Path repo) throws IOException {
    Files.createDirectories(repo);
    writeSettings(projectDir, repositoriesBlock(repo), "app");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"), "plugins { id(\"ai.narrativetrace\") }\n");
    writeJavaModule(projectDir, "app", false);

    var result =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("narrativetraceInit")
            .buildAndFail();

    assertThat(result.getOutput())
        .contains("could not resolve the NarrativeTrace skills carrier")
        .contains("no repository in this build provides ai.narrativetrace:narrativetrace-skills");
  }

  // --- helpers -------------------------------------------------------------------------------

  private void writeThreeProjectBuild(Path projectDir, Path repo) throws IOException {
    writeSettings(projectDir, repositoriesBlock(carrierRepository(repo)), "a", "b");
    Files.writeString(
        projectDir.resolve("build.gradle.kts"), "plugins { id(\"ai.narrativetrace\") }\n");
    writeJavaModule(projectDir, "a", true);
    writeJavaModule(projectDir, "b", true);
  }

  private static BuildResult run(Path projectDir, String... args) {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .build();
  }

  private static String repositoriesBlock(Path repo) {
    return "dependencyResolutionManagement { repositories { maven { url = uri(\""
        + repo.toUri()
        + "\") } } }\n";
  }

  private static void writeSettings(Path projectDir, String repositories, String... modules)
      throws IOException {
    var includes = new StringBuilder();
    for (String module : modules) {
      includes.append("include(\"").append(module).append("\")\n");
    }
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"layout\"\n" + repositories + includes);
  }

  private static void writeJavaModule(Path projectDir, String name, boolean withPlugin)
      throws IOException {
    Path module = projectDir.resolve(name);
    Files.createDirectories(module);
    Files.writeString(
        module.resolve("build.gradle.kts"),
        "plugins {\n    `java-library`\n"
            + (withPlugin ? "    id(\"ai.narrativetrace\")\n" : "")
            + "}\n");
  }

  private static boolean nothingNarrativeTraceWroteUnder(Path module) throws IOException {
    try (Stream<Path> walk = Files.walk(module)) {
      return walk.map(module::relativize)
          .map(Path::toString)
          .noneMatch(
              p -> p.startsWith(".agents") || p.startsWith(".claude") || p.equals("AGENTS.md"));
    }
  }

  private static TreeMap<String, String> tree(Path projectDir) throws IOException {
    var files = new TreeMap<String, String>();
    try (Stream<Path> walk = Files.walk(projectDir)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        String relative = projectDir.relativize(file).toString();
        if (!relative.startsWith("build")
            && !relative.startsWith(".gradle")
            && !relative.contains("/build/")) {
          files.put(relative, Files.readString(file));
        }
      }
    }
    return files;
  }

  private static Path carrierRepository(Path repo) throws IOException {
    Path target = repo.resolve("ai/narrativetrace/narrativetrace-skills/" + CARRIER_VERSION);
    Files.createDirectories(target);
    Files.copy(
        REPOSITORY_ROOT.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + CARRIER_VERSION + ".jar"),
        target.resolve("narrativetrace-skills-" + CARRIER_VERSION + ".jar"));
    Files.writeString(
        target.resolve("narrativetrace-skills-" + CARRIER_VERSION + ".pom"),
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>ai.narrativetrace</groupId>\n"
            + "  <artifactId>narrativetrace-skills</artifactId>\n"
            + "  <version>"
            + CARRIER_VERSION
            + "</version>\n  <packaging>jar</packaging>\n</project>\n");
    return repo;
  }
}
