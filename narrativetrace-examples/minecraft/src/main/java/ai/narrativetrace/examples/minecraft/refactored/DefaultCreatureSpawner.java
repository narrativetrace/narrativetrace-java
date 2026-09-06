/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

public class DefaultCreatureSpawner implements CreatureSpawner {

  @Override
  public Creature spawnHostile(CreatureType type, int x, int y, int z) {
    return new Creature(type, x, y, z);
  }
}
