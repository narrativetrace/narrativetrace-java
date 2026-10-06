/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.skills.catalogue.CatalogueIndex;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The closed vocabulary of registry deliveries a Tier B case may declare. A case names one by its
 * id in {@code case.json}; anything else is a case that would silently run no pre-step at all,
 * which is the one outcome a registry case must never have.
 */
class RegistryPreStepTest {

  private static final Path REPO = Path.of("/repo");
  private static final Path STAGED = Path.of("/scratch/staged");

  @Test
  void parsesTheTwoDocumentedRegistryIdsAndNothingElse() {
    assertThat(RegistryPreStep.parse("claude-marketplace"))
        .contains(RegistryPreStep.CLAUDE_MARKETPLACE);
    assertThat(RegistryPreStep.parse("npx-skills")).contains(RegistryPreStep.NPX_SKILLS);
    assertThat(RegistryPreStep.parse("gemini-skills")).isEmpty();
    assertThat(RegistryPreStep.parse("")).isEmpty();
    assertThat(RegistryPreStep.parse(null)).isEqualTo(Optional.empty());
  }

  /**
   * The documented Claude Code lines, in the order a reader runs them: add the marketplace, then
   * install its one plugin. The third command is the pre-step's own self-check — a plugin whose
   * inventory cannot be read is not installed, and `details` exits non-zero for one that is not
   * there, so a silently empty install can never pass for a delivered one.
   */
  @Test
  void theMarketplaceDeliveryStagesThenAddsInstallsAndProvesTheInstall() {
    String id = CatalogueIndex.MARKETPLACE.name() + "@" + CatalogueIndex.MARKETPLACE.name();

    List<List<String>> commands = RegistryPreStep.CLAUDE_MARKETPLACE.commands(REPO, STAGED);

    assertThat(commands.subList(0, 2)).isEqualTo(StagedSnapshot.stagingCommands(REPO, STAGED));
    assertThat(commands.subList(2, commands.size()))
        .containsExactly(
            List.of("claude", "plugin", "marketplace", "add", "/scratch/staged"),
            List.of("claude", "plugin", "install", id),
            List.of("claude", "plugin", "details", id));
  }

  /**
   * The {@code npx} line, unpinned on purpose: the case exists to keep a PUBLISHED line honest, and
   * a reader types no version. Pinning one would freeze the replay against a tool build no reader
   * gets, which is the one failure the case is here to catch. The resolved version belongs in the
   * run's record, not in the argv.
   */
  @Test
  void theNpxDeliveryStagesThenRunsTheDocumentedAddLineUnpinned() {
    List<List<String>> commands = RegistryPreStep.NPX_SKILLS.commands(REPO, STAGED);

    assertThat(commands.subList(0, 2)).isEqualTo(StagedSnapshot.stagingCommands(REPO, STAGED));
    assertThat(commands.subList(2, commands.size()))
        .containsExactly(List.of("npx", "--yes", "skills", "add", "/scratch/staged", "-y"));
  }

  /**
   * User scope only: the documented install is the personal one, and no registry command this
   * harness runs may declare a project scope — a project-scope marketplace writes a settings file
   * INTO the graded project, which is state the case never asked for and the installer would then
   * be measured against.
   */
  @Test
  void noDeliveryEverAsksForAProjectScope() {
    for (RegistryPreStep step : RegistryPreStep.values()) {
      assertThat(step.commands(REPO, STAGED))
          .allSatisfy(command -> assertThat(command).doesNotContain("--scope", "project"));
    }
  }
}
