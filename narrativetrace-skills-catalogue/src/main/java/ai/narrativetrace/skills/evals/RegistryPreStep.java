/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * INTENT: the closed vocabulary of deliveries a Tier B case may declare — the step that puts this
 * repository's rendered skill pages in place before the agent starts: a REGISTRY tool, or our own
 * installer run by the harness so that a case can ask what a session finds AFTER an install without
 * the install itself being the agent's work.
 *
 * <p>A closed enum rather than a command string in {@code case.json}: a case file is data, and data
 * that can name any executable is a shell this harness does not have. Each constant owns the exact
 * argv a documented registry line amounts to, so the case replays what a reader runs.
 *
 * @llmNote The id is what a case's {@code case.json} carries ({@code "registry": "npx-skills"}); an
 *     unknown id is an error at the parse site, never a silently skipped pre-step.
 */
public enum RegistryPreStep {

  /** The Claude Code plugin marketplace: added, then its one plugin installed at user scope. */
  CLAUDE_MARKETPLACE("claude-marketplace"),

  /** {@code npx skills add}: the open-standard pages installed into the project itself. */
  NPX_SKILLS("npx-skills"),
  /**
   * Our own installer, {@code narrativetrace init} from the built CLI jar, run by the harness in
   * the scratch project: nothing is staged, the carrier is the one bundled with the jar — this
   * checkout's own rendered pages — so what the case then measures is the install under test, never
   * a published package that predates it (the ports' trap, 2026-10-10) and never the agent's own
   * reading of the install prompt (two of three runs stopped before the install).
   */
  NARRATIVETRACE_INIT("narrativetrace-init");

  private final String id;

  RegistryPreStep(String id) {
    this.id = id;
  }

  /** What a case's {@code case.json} names this delivery by. */
  public String id() {
    return id;
  }

  /** The delivery {@code id} names, or empty when nothing in the vocabulary matches it. */
  public static Optional<RegistryPreStep> parse(String id) {
    return Arrays.stream(values()).filter(step -> step.id.equals(id)).findFirst();
  }

  /**
   * Everything this delivery runs before the agent starts: the staging of {@link
   * StagedSnapshot#REGISTRY_SURFACE} out of {@code HEAD} of the repository at {@code repoRoot},
   * then the registry tool's own documented commands against the staged tree.
   *
   * <p>The staged tree stands in for the GitHub shorthand a reader types, because the public
   * repository's own {@code main} carries the pages of the release BEFORE the one these cases
   * describe — the same reason the design sequences everything a user runs against GitHub after the
   * carrier release.
   */
  public List<List<String>> commands(Path repoRoot, Path stagedSnapshot) {
    if (this == NARRATIVETRACE_INIT) {
      return List.of(List.of("java", "-jar", CliJar.require(repoRoot).toString(), "init"));
    }
    List<List<String>> all =
        new ArrayList<>(StagedSnapshot.stagingCommands(repoRoot, stagedSnapshot));
    all.addAll(registryCommands(stagedSnapshot));
    return List.copyOf(all);
  }

  /** The tool's own lines — what the documentation tells a reader to run, and nothing beside. */
  private List<List<String>> registryCommands(Path stagedSnapshot) {
    String plugin = CatalogueIndex.MARKETPLACE.name();
    String installId = plugin + "@" + plugin;
    return switch (this) {
      case CLAUDE_MARKETPLACE ->
          List.of(
              List.of("claude", "plugin", "marketplace", "add", stagedSnapshot.toString()),
              List.of("claude", "plugin", "install", installId),
              List.of("claude", "plugin", "details", installId));
      case NPX_SKILLS ->
          List.of(List.of("npx", "--yes", "skills", "add", stagedSnapshot.toString(), "-y"));
      case NARRATIVETRACE_INIT -> throw new IllegalStateException("the installer stages nothing");
    };
  }
}
