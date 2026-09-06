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

class PlayerInventoryTest {

  @Test
  void addItemReturnsTrue() {
    PlayerInventory inventory = new DefaultPlayerInventory();

    boolean added = inventory.addItem(Item.WOODEN_PICKAXE, 1);

    assertThat(added).isTrue();
  }
}
