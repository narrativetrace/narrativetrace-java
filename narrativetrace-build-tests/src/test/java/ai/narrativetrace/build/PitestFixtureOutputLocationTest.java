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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PIT's own processes (coverage-analysis "main" JVM, per-mutant "minion" JVMs) run {@code
 * narrativetrace-junit5}'s own fixtures directly and, unlike {@code tasks.test}, are outside that
 * module's {@code workingDir} control. A real {@code :pitest} run wrote {@code manifest.json},
 * {@code clarity-report.md}, {@code clarity-results.json} and {@code traces/} straight into the
 * module's source root — untracked, un-ignored, and left for the nightly's clean-tree check to
 * refuse the next commit over (2026-09-17 nightly F1).
 *
 * <p>The first attempt at a guard pinned {@code narrativetrace.outputDir} and ran PIT scoped to one
 * fixture with {@code -PpitestTargetTests}. It was green while the nightly stayed red, for one
 * reason worth keeping: <strong>the invocation under test was not the invocation that
 * breaks</strong>. Scoped to a single fixture, PIT never generates the mutant that causes this —
 * {@code NarrativeTraceExtension.configParam} returns a String, so PIT's {@code
 * EmptyObjectReturnValsMutator} makes a minion in which every configuration lookup returns {@code
 * ""}, the pinned property included, and {@code Path.of("")} resolves against the process working
 * directory. No property survives a mutant of its own reader. So this test now runs the UNSCOPED
 * invocation the nightly runs, and the fix it guards is a working directory under {@code build/},
 * not a property.
 *
 * <p>That honesty costs a full module mutation run (~1-2 minutes, four concurrent JVMs), so the
 * expensive half carries {@code @Tag("mutation")} and runs on the scheduled job beside {@code
 * :pitest} itself, never in a per-commit {@code check}. What {@code check} keeps is {@link
 * #pitestRunsInsideTheModulesBuildDirectory()}: seconds, and enough to catch the configuration
 * being dropped or pointed back at the source root.
 *
 * <p>Runs against THIS repository directly, via {@link GradleRunner}, never a copied fixture: the
 * bug is specifically about where PIT's own processes write relative to a real module's source
 * root, which a throwaway project cannot reproduce. Never {@code clean} — a live module's other
 * build outputs must survive this test (2026-09-16 lesson, {@link ReproducibleJarTest}) — and any
 * stray output is removed in {@link #removeAnyStrayOutput()} regardless of outcome, so a red run
 * never leaves the tree dirty for the next commit.
 */
class PitestFixtureOutputLocationTest {

  private static final File PROJECT_DIR = new File(System.getProperty("projectDir"));
  private static final File MODULE_DIR = new File(PROJECT_DIR, "narrativetrace-junit5");
  private static final List<String> STRAY_PATHS =
      List.of(
          "manifest.json",
          "clarity-report.md",
          "clarity-results.json",
          "traces",
          "glossary.json",
          "glossary.md");

  private record ProcessResult(String output, int exitCode) {}

  private static ProcessResult run(ProcessBuilder processBuilder)
      throws IOException, InterruptedException {
    var process = processBuilder.redirectErrorStream(true).start();
    var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var exitCode = process.waitFor();
    return new ProcessResult(output, exitCode);
  }

  private static List<String> moduleStatus() throws IOException, InterruptedException {
    var status =
        run(
            new ProcessBuilder("git", "status", "--porcelain", "narrativetrace-junit5")
                .directory(PROJECT_DIR));
    assertThat(status.exitCode()).as("git status: %s", status.output()).isZero();
    return status.output().lines().sorted().toList();
  }

  @AfterEach
  void removeAnyStrayOutput() throws IOException, InterruptedException {
    for (var name : STRAY_PATHS) {
      if (new File(MODULE_DIR, name).exists()) {
        run(new ProcessBuilder("git", "clean", "-fdx", "--", name).directory(MODULE_DIR));
      }
    }
  }

  /**
   * The cheap half, in every {@code check}: the build itself answers where PIT's processes run,
   * rather than a regex over {@code narrativetrace-junit5/build.gradle.kts} that would keep passing
   * after the configuration it reads was deleted.
   */
  @Test
  void pitestRunsInsideTheModulesBuildDirectory() {
    var printed =
        GradleRunner.create()
            .withProjectDir(PROJECT_DIR)
            .withArguments(":narrativetrace-junit5:printPitestWorkingDir", "-q")
            .build()
            .getOutput()
            .strip();

    assertThat(new File(printed))
        .as("PIT's processes must run below the module's build directory, never in its source root")
        .isAbsolute()
        .hasParent(new File(MODULE_DIR, "build"));
  }

  /**
   * The expensive half: the nightly's own invocation, unscoped — {@code
   * :narrativetrace-junit5:pitest} with no {@code targetTests} narrowing, so PIT generates and runs
   * the empty-String-return mutant that defeats any configuration-based pin.
   */
  @Test
  @Tag("mutation")
  void pitestNeverWritesFixtureOutputOutsideBuild() throws IOException, InterruptedException {
    // What the run must not change, rather than a pristine module: this test has to pass while the
    // very build script it guards is being edited, and asserting an empty `git status` fails on the
    // author's own uncommitted change instead of on the defect.
    var before = moduleStatus();

    // GradleRunner, not a raw `./gradlew` subprocess: TaskInputInvalidationTest already runs
    // nested builds against this same PROJECT_DIR from inside this module's own suite, and
    // GradleRunner's isolated test-kit Gradle user home is what makes that safe alongside the
    // outer build holding this project's own locks. `--rerun` because an UP-TO-DATE pitest task
    // would run no JVM at all and assert nothing.
    var result =
        GradleRunner.create()
            .withProjectDir(PROJECT_DIR)
            .withArguments(":narrativetrace-junit5:pitest", "--rerun", "-q")
            .build();
    assertThat(result.task(":narrativetrace-junit5:pitest")).isNotNull();

    assertThat(moduleStatus())
        .as("PIT's processes must never write fixture output into the module's source tree")
        .isEqualTo(before);
    // Named separately because `git status` alone cannot see them: glossary.json and glossary.md
    // are .gitignored in this module, so a run that wrote them into the source root would leave a
    // clean-looking tree and a hand-removable file — the same defect, invisible to the check above.
    assertThat(STRAY_PATHS.stream().filter(name -> new File(MODULE_DIR, name).exists()).toList())
        .as("no fixture artifact may sit in the module's source root, ignored or not")
        .isEmpty();
  }
}
