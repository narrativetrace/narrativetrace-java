/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

public class DefaultWorldServer implements WorldServer {

  private final WorldGenerator worldGenerator;
  private final PlayerInventory inventory;
  private final CraftingTable craftingTable;
  private final CreatureSpawner creatureSpawner;

  public DefaultWorldServer(
      WorldGenerator worldGenerator,
      PlayerInventory inventory,
      CraftingTable craftingTable,
      CreatureSpawner creatureSpawner) {
    this.worldGenerator = worldGenerator;
    this.inventory = inventory;
    this.craftingTable = craftingTable;
    this.creatureSpawner = creatureSpawner;
  }

  @Override
  public String playerJoined(String playerName) {
    worldGenerator.generateChunk(0, 0);
    inventory.addItem(Item.OAK_LOG, 4);
    inventory.addItem(Item.COBBLESTONE, 8);
    Item tool = craftingTable.craft(Recipe.WOODEN_PICKAXE);
    inventory.addItem(tool, 1);
    creatureSpawner.spawnHostile(CreatureType.ZOMBIE, 10, 64, 20);
    return playerName + " joined the world";
  }
}
