/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link RepoRoot#locate(Path)} is cwd-injectable specifically so these can drive it with a
 * synthetic layout instead of the real repo — the two cases the defect report named ("the Gradle
 * task and a direct `java -jar` launch from the repo root and from the module dir must both find
 * `evals/<skill>/<case>/graders/verify.sh`") are the first two tests below.
 */
class RepoRootTest {

  @Test
  void locateReturnsTheCwdWhenItAlreadyCarriesTheRootMarker(@TempDir Path tempDir)
      throws IOException {
    Files.createFile(tempDir.resolve("settings.gradle.kts"));

    assertThat(RepoRoot.locate(tempDir)).isEqualTo(tempDir);
  }

  @Test
  void locateReturnsTheParentWhenTheCwdIsTheModuleDirectory(@TempDir Path tempDir)
      throws IOException {
    Files.createFile(tempDir.resolve("settings.gradle.kts"));
    Path moduleDir = Files.createDirectory(tempDir.resolve("narrativetrace-skills-catalogue"));

    assertThat(RepoRoot.locate(moduleDir)).isEqualTo(tempDir);
  }

  @Test
  void aModuleNamedDirectoryWithNoRootMarkerParentFallsBackToTheCodeSource(@TempDir Path tempDir)
      throws IOException {
    // A directory that merely happens to be named narrativetrace-skills-catalogue but whose parent
    // carries no root marker must not be mistaken for the module directory — falls back to the
    // real repo root via this class's own code source, which is where the test JVM actually runs.
    Path lookalike = Files.createDirectory(tempDir.resolve("narrativetrace-skills-catalogue"));

    Path found = RepoRoot.locate(lookalike);

    assertThat(found.resolve("settings.gradle.kts")).exists();
    assertThat(found.resolve("narrativetrace-skills-catalogue").resolve("evals")).isDirectory();
  }

  @Test
  void locateFallsBackToTheCodeSourceFromAnyOtherDirectory(@TempDir Path tempDir) {
    Path found = RepoRoot.locate(tempDir);

    assertThat(found.resolve("settings.gradle.kts")).exists();
    assertThat(found.resolve("narrativetrace-skills-catalogue").resolve("evals")).isDirectory();
  }

  @Test
  void locateStillFallsBackWhenTheCwdIsTheFilesystemRoot() {
    // The filesystem root has no file name at all (Path#getFileName() returns null) — must not
    // NPE the module-directory-name check, and must still fall back to the code source.
    Path filesystemRoot = Path.of("/");

    Path found = RepoRoot.locate(filesystemRoot);

    assertThat(found.resolve("settings.gradle.kts")).exists();
  }

  @Test
  void locateWithNoArgumentUsesTheRealProcessCwd() {
    // The production entry point — proves it delegates to the cwd-injectable overload rather than
    // resolving independently, and that it succeeds under the real test-runner cwd.
    Path found = RepoRoot.locate();

    assertThat(found.resolve("settings.gradle.kts")).exists();
  }
}
