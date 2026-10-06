/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * INTENT: how the case a runner is driving got its skill pages — by a registry, or by the harness
 * itself. One value answers all three questions that follow from it: whether the harness still
 * copies the rendered pages in, what runs before the agent starts, and which configuration
 * directory every command of the trial uses.
 *
 * <p>Both fields or neither: a step without a work directory would run a vendor tool against the
 * ambient configuration, and a work directory without a step would isolate a trial that installs
 * nothing.
 *
 * @param preStep the registry delivery, or {@code null} for the ordinary case
 * @param workDir a directory the trial owns and the runner deletes — the isolated vendor-tool
 *     configuration and the staged snapshot both live inside it, never inside the graded project
 * @llmNote One instance per case, built once by the runner: the delivery is a property of the case,
 *     not of a trial, and every trial of one case shares it.
 */
public record RegistryDelivery(RegistryPreStep preStep, Path workDir) {

  private static final RegistryDelivery NONE = new RegistryDelivery(null, null);

  public RegistryDelivery {
    if ((preStep == null) != (workDir == null)) {
      throw new IllegalArgumentException(
          "a RegistryDelivery carries a pre-step and the work directory it runs in, or neither");
    }
  }

  /** The ordinary case: the harness copies the rendered pages in and runs nothing before it. */
  public static RegistryDelivery none() {
    return NONE;
  }

  /** A case whose pages {@code preStep} delivers, with {@code workDir} to itself. */
  public static RegistryDelivery through(RegistryPreStep preStep, Path workDir) {
    if (preStep == null || workDir == null) {
      throw new IllegalArgumentException(
          "a registry delivery needs both a pre-step and a work directory");
    }
    return new RegistryDelivery(preStep, workDir);
  }

  /**
   * Whether a registry puts this case's pages in place. When it does, the harness copies NONE of
   * its own rendered pages in: what such a case measures is the state the registry left behind, and
   * a page the harness wrote over it would answer the case's own question for it.
   */
  public boolean deliversTheSkills() {
    return preStep != null;
  }

  /** The tree the registry tool reads, outside the graded project. */
  public Path stagedSnapshot() {
    if (!deliversTheSkills()) {
      throw new IllegalStateException("a delivery that delivers nothing stages nothing");
    }
    return workDir.resolve("staged");
  }

  /** Everything that runs before the agent starts, in order; empty for the ordinary case. */
  public List<List<String>> commands(Path repoRoot) {
    return deliversTheSkills() ? preStep.commands(repoRoot, stagedSnapshot()) : List.of();
  }

  /** The environment every command of this trial runs with; empty for the ordinary case. */
  public Map<String, String> environment() {
    return deliversTheSkills() ? IsolatedAgentConfig.env(workDir) : Map.of();
  }
}
