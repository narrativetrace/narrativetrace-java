/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.refactored;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class WorldServerTest {

  @Test
  void playerJoinedOrchestratesWorldGenInventoryCraftingAndSpawning(NarrativeContext context) {
    var worldGenerator =
        NarrativeTraceProxy.trace(new DefaultWorldGenerator(), WorldGenerator.class, context);
    var inventory =
        NarrativeTraceProxy.trace(new DefaultPlayerInventory(), PlayerInventory.class, context);
    var craftingTable =
        NarrativeTraceProxy.trace(new DefaultCraftingTable(), CraftingTable.class, context);
    var spawner =
        NarrativeTraceProxy.trace(new DefaultCreatureSpawner(), CreatureSpawner.class, context);

    var server =
        NarrativeTraceProxy.trace(
            new DefaultWorldServer(worldGenerator, inventory, craftingTable, spawner),
            WorldServer.class,
            context);

    String result = server.playerJoined("Steve");

    assertThat(result).contains("Steve");

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(narrative).contains("WorldServer.playerJoined");
    assertThat(narrative).contains("WorldGenerator.generateChunk");
    assertThat(narrative).contains("PlayerInventory.addItem");
    assertThat(narrative).contains("CraftingTable.craft");
    assertThat(narrative).contains("CreatureSpawner.spawnHostile");
  }
}
