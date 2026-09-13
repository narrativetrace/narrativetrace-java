/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
    Path moduleDir = Files.createDirectory(tempDir.resolve("narrativetrace-skills"));

    assertThat(RepoRoot.locate(moduleDir)).isEqualTo(tempDir);
  }

  @Test
  void aModuleNamedDirectoryWithNoRootMarkerParentFallsBackToTheCodeSource(@TempDir Path tempDir)
      throws IOException {
    // A directory that merely happens to be named narrativetrace-skills but whose parent carries
    // no root marker must not be mistaken for the module directory — falls back to the real repo
    // root via this class's own code source, which is where the test JVM actually runs from.
    Path lookalike = Files.createDirectory(tempDir.resolve("narrativetrace-skills"));

    Path found = RepoRoot.locate(lookalike);

    assertThat(found.resolve("settings.gradle.kts")).exists();
    assertThat(found.resolve("narrativetrace-skills").resolve("evals")).isDirectory();
  }

  @Test
  void locateFallsBackToTheCodeSourceFromAnyOtherDirectory(@TempDir Path tempDir) {
    Path found = RepoRoot.locate(tempDir);

    assertThat(found.resolve("settings.gradle.kts")).exists();
    assertThat(found.resolve("narrativetrace-skills").resolve("evals")).isDirectory();
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
