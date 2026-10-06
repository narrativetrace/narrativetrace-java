/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** How a case's skill pages got into the project, for the one case a runner is driving. */
class RegistryDeliveryTest {

  private static final Path REPO = Path.of("/repo");
  private static final Path WORK = Path.of("/work");

  @Test
  void theOrdinaryCaseDeliversNothingRunsNothingAndChangesNoEnvironment() {
    RegistryDelivery delivery = RegistryDelivery.none();

    assertThat(delivery.deliversTheSkills()).isFalse();
    assertThat(delivery.commands(REPO)).isEmpty();
    assertThat(delivery.environment()).isEmpty();
  }

  @Test
  void aRegistryCaseRunsThatRegistrysCommandsAgainstItsOwnStagedTree() {
    RegistryDelivery delivery = RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, WORK);

    assertThat(delivery.deliversTheSkills()).isTrue();
    assertThat(delivery.stagedSnapshot()).isEqualTo(WORK.resolve("staged"));
    assertThat(delivery.commands(REPO))
        .isEqualTo(RegistryPreStep.NPX_SKILLS.commands(REPO, WORK.resolve("staged")));
    assertThat(delivery.environment()).isEqualTo(IsolatedAgentConfig.env(WORK));
  }

  /**
   * A delivery with a step but no work directory would run a vendor tool against the ambient
   * configuration — the one outcome the isolation exists to prevent — and one with a work directory
   * but no step would isolate a trial that installs nothing. Neither half is optional.
   */
  @Test
  void refusesHalfADelivery() {
    assertThatThrownBy(() -> RegistryDelivery.through(RegistryPreStep.NPX_SKILLS, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RegistryDelivery.through(null, WORK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aDeliveryThatDeliversNothingHasNoStagedTreeToName() {
    assertThatThrownBy(() -> RegistryDelivery.none().stagedSnapshot())
        .isInstanceOf(IllegalStateException.class);
  }
}
