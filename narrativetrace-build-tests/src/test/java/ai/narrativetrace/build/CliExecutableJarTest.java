/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves {@code narrativetrace-cli}'s published jar is EXECUTABLE on its own, by executing it.
 *
 * <p>The jar declares a {@code Main-Class}, so {@code java -jar narrativetrace-cli-<v>.jar} is
 * something a person and a skill both reasonably type — and the doctor's classes live in a separate
 * module since the 2026-09-24 tooling split, which is exactly the shape that turns an advertised
 * entry point into a {@code NoClassDefFoundError} nobody notices until a release. Asserting the
 * bundled classes are present would not have caught it either: only running the jar does.
 */
class CliExecutableJarTest {

  @Test
  void theJarDeclaresItsMainClass() throws IOException {
    try (var zip = new ZipFile(CliJar.jar())) {
      ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
      assertThat(manifest).isNotNull();
      try (var stream = zip.getInputStream(manifest)) {
        assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
            .contains("Main-Class: ai.narrativetrace.cli.Main");
      }
    }
  }

  @Test
  void javaDashJarPrintsUsageAndExitsZero(@TempDir Path workingDir)
      throws IOException, InterruptedException {
    var run = CliJar.run(workingDir, "--help");

    assertThat(run.output()).contains("narrativetrace doctor [--json]");
    assertThat(run.exitCode()).isZero();
  }

  /**
   * The verb that actually reaches the tooling library: a snapshot walk, twenty checks and a
   * rendered report, none of which lives in this jar's own package. Exit 1 is a normal doctor
   * outcome (findings present) on an empty directory; exit 2 would mean it could not run at all.
   */
  @Test
  void javaDashJarRunsTheDoctorVerbAgainstADirectory(@TempDir Path workingDir)
      throws IOException, InterruptedException {
    var run = CliJar.run(workingDir, "doctor", "--json");

    assertThat(run.output()).contains("\"findings\"").contains("\"exitCode\"");
    assertThat(run.exitCode()).isIn(0, 1);
  }

  /**
   * The verb that reaches the CARRIER: the skills this jar bundles as resources (Phase 2 D4), read
   * back out of the running jar by the installer library. A classpath that is right in the IDE and
   * wrong in the jar is exactly the failure mode `java -jar` catches and a unit test does not —
   * this one would go red if the resources ever stopped being packaged, or if the launcher looked
   * for them somewhere its own code source cannot see.
   */
  @Test
  void javaDashJarPlansAnInstallFromTheCarrierItBundles(@TempDir Path workingDir)
      throws IOException, InterruptedException {
    var run = CliJar.run(workingDir, "init", "--dry-run");

    assertThat(run.exitCode()).isZero();
    assertThat(run.output())
        .contains("narrativetrace — ai.narrativetrace:narrativetrace-cli:" + CliJar.BUILD_VERSION)
        .contains("+++ b/AGENTS.md")
        .contains("+++ b/.agents/skills/narrativetrace-doctor/SKILL.md")
        .contains("installed by narrativetrace init from");
    assertThat(workingDir.resolve("AGENTS.md")).doesNotExist();
    assertThat(workingDir.resolve(".agents")).doesNotExist();
  }

  @Test
  void javaDashJarPrintsTheInstallerEnvelopeAsJson(@TempDir Path workingDir)
      throws IOException, InterruptedException {
    var run = CliJar.run(workingDir, "init", "--dry-run", "--json");

    assertThat(run.exitCode()).isZero();
    assertThat(run.output())
        .contains("\"carrier\"")
        .contains("\"actions\"")
        .contains("\"exitCode\": 0");
  }
}
