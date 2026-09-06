/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

public enum Recipe {
  WOODEN_PICKAXE(Item.WOODEN_PICKAXE),
  OAK_PLANKS(Item.OAK_PLANKS),
  STICK(Item.STICK);

  private final Item result;

  Recipe(Item result) {
    this.result = result;
  }

  public Item result() {
    return result;
  }
}
