/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * INTENT: the tree a registry reads — this repository's registry surface as a clone of the public
 * repository would show it, staged out of {@code HEAD} into a scratch directory.
 *
 * <p>Out of {@code HEAD} and never out of the working tree, for the same reason the publish
 * script's own staging and the build's vendor validation do it that way: a registry serves what was
 * committed, so a trial run against uncommitted edits would grade a tree no reader can get.
 *
 * @llmNote Two argv lists, never a shell string: {@code git archive} writes the tar, {@code tar}
 *     unpacks it, and neither argument is ever reinterpreted. The caller creates the target
 *     directory and runs both through the harness's own process seam.
 */
public final class StagedSnapshot {

  /**
   * What a registry actually reads: the plugin marketplace file and both rendered page flavours.
   * Every documented registry tool scans some subset of these three and nothing else of the
   * repository, so staging exactly them keeps a trial's tree honest in both directions — nothing a
   * reader cannot see, and nothing a reader can see left out.
   */
  public static final List<String> REGISTRY_SURFACE =
      List.of(".claude-plugin", ".claude/skills", ".agents/skills");

  private static final String ARCHIVE_SUFFIX = ".tar";

  private StagedSnapshot() {}

  /**
   * The commands that fill {@code into} with {@link #REGISTRY_SURFACE} as of {@code HEAD} of the
   * repository at {@code repoRoot}. Both run with any working directory — every path is absolute,
   * and {@code git} is pointed at the repository with {@code -C} rather than inheriting it.
   */
  public static List<List<String>> stagingCommands(Path repoRoot, Path into) {
    String archive = archiveBeside(into);
    List<String> archiveCommand =
        new ArrayList<>(
            List.of(
                "git",
                "-C",
                repoRoot.toString(),
                "archive",
                "--format=tar",
                "-o",
                archive,
                "HEAD",
                "--"));
    archiveCommand.addAll(REGISTRY_SURFACE);
    return List.of(
        List.copyOf(archiveCommand), List.of("tar", "-xf", archive, "-C", into.toString()));
  }

  /**
   * The tarball's own path: a sibling of the staged tree, so the tree holds exactly what {@code git
   * archive} put there. A registry tool scans every file under the root it is given, and the
   * intermediate artefact of staging is not something a clone of the public repository shows.
   */
  private static String archiveBeside(Path into) {
    Path name = into.getFileName();
    return into.resolveSibling(name + ARCHIVE_SUFFIX).toString();
  }
}
