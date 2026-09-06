/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreatureSpawnerTest {

  @Test
  void spawnHostileReturnsCreature() {
    CreatureSpawner spawner = new DefaultCreatureSpawner();

    Creature creature = spawner.spawnHostile(CreatureType.ZOMBIE, 10, 64, 20);

    assertThat(creature.type()).isEqualTo(CreatureType.ZOMBIE);
    assertThat(creature.x()).isEqualTo(10);
    assertThat(creature.y()).isEqualTo(64);
    assertThat(creature.z()).isEqualTo(20);
  }
}
