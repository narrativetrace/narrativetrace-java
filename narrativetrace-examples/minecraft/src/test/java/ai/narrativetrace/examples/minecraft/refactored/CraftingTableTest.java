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

class CraftingTableTest {

  @Test
  void craftReturnsCraftedItem() {
    CraftingTable table = new DefaultCraftingTable();

    Item result = table.craft(Recipe.WOODEN_PICKAXE);

    assertThat(result).isEqualTo(Item.WOODEN_PICKAXE);
  }
}
