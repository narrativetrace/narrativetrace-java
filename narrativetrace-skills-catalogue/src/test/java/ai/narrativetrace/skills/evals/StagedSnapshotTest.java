/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A registry reads a git repository, so a registry case must be handed the same tree a clone of the
 * public repository would show — staged out of {@code HEAD}, never out of the working tree, exactly
 * the way the publish script's own staging and {@code vendorValidate} do it.
 */
class StagedSnapshotTest {

  private static final Path REPO = Path.of("/repo");
  private static final Path INTO = Path.of("/scratch/staged");

  @Test
  void stagesHeadsRegistrySurfaceAsTwoArgvListsAndNoShell() {
    List<List<String>> commands = StagedSnapshot.stagingCommands(REPO, INTO);

    assertThat(commands)
        .containsExactly(
            List.of(
                "git",
                "-C",
                "/repo",
                "archive",
                "--format=tar",
                "-o",
                "/scratch/staged.tar",
                "HEAD",
                "--",
                ".claude-plugin",
                ".claude/skills",
                ".agents/skills"),
            List.of("tar", "-xf", "/scratch/staged.tar", "-C", "/scratch/staged"));
  }

  /**
   * The tar is an artefact of HOW the tree got here, and a registry tool scans everything it is
   * pointed at — so the archive lands BESIDE the staged tree, never in it. A clone of the public
   * repository carries no tarball at its root, and a staged tree that does is no longer the thing
   * the case claims to be measuring.
   */
  @Test
  void writesTheArchiveBesideTheStagedTreeAndNeverInsideIt() {
    List<List<String>> commands = StagedSnapshot.stagingCommands(REPO, INTO);

    assertThat(commands)
        .allSatisfy(
            command ->
                assertThat(command)
                    .noneMatch(
                        argument -> argument.startsWith(INTO + "/") && argument.endsWith(".tar")));
  }

  /**
   * `git archive` fails outright on a path that is not in the tree, and it would fail at TRIAL time
   * — after the commit that renamed the path, in a run whose red row says nothing about the
   * product. So the surface is checked against the repository on every build instead.
   */
  @Test
  void everyStagedPathIsReallyInThisRepository() {
    Path repoRoot = Path.of(System.getProperty("projectDir"));

    assertThat(StagedSnapshot.REGISTRY_SURFACE)
        .allSatisfy(
            relative ->
                assertThat(Files.exists(repoRoot.resolve(relative)))
                    .as(relative + " is staged for every registry case, so it must exist")
                    .isTrue());
  }
}
