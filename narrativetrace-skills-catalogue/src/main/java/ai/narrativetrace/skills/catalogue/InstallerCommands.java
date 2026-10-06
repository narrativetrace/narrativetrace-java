/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

/**
 * The installer's adopter-facing command literals, beside {@link DoctorCommands} and {@link
 * ClarityCommands} — one home per concept, so a second hand-copied literal is a duplication-ratchet
 * failure rather than a silent divergence.
 *
 * <p>INTENT: a skill may PREVIEW the install and may never apply it. The preview is the whole
 * safety property: {@code init} writes into {@code AGENTS.md} and {@code .agents/skills/}, and the
 * approval for that is structural — a person reads the diff and runs the command again without the
 * flag. That is why only the previewing form is a constant here; the applying form is deliberately
 * not available to a step.
 *
 * <p><b>@llmNote</b> The flag is {@code --diff}, not {@code --dry-run}. Gradle's own built-in
 * {@code --dry-run} skips every task in the graph, so a task option of that name never executes;
 * the CLI verb, where nothing shadows it, keeps {@code --dry-run}. Both set the same option.
 */
public final class InstallerCommands {

  /** Previews what {@code narrativetraceInit} would write, and writes nothing. */
  public static final String PREVIEW_INSTALL = "./gradlew narrativetraceInit --diff";

  /**
   * The preview's definition of done, in prose: a diff to read, and a tree nobody has touched.
   *
   * <p>Prose rather than a command because the second half — "nothing was written" — is the part a
   * shell exit code cannot express, and it is the half that matters.
   */
  public static final String VERIFY_PREVIEW_WROTE_NOTHING =
      "the diff names .agents/skills/ and AGENTS.md, and neither exists yet — nothing is written"
          + " until the same command runs again without --diff";

  private InstallerCommands() {}
}
