/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft;

import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.examples.AsciiSequenceDiagram;
import ai.narrativetrace.examples.DemoTraces;
import ai.narrativetrace.examples.minecraft.refactored.*;
import ai.narrativetrace.examples.minecraft.unrefactored.*;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tutorial runner that contrasts refactored and unrefactored traces.
 *
 * <p>The two halves of this example deliberately do similar work so developers can focus on the one
 * thing that changes: naming quality. The refactored half uses domain-rich names; the unrefactored
 * half hides the same intent behind generic labels.
 *
 * <h2>How to use this example</h2>
 *
 * <ol>
 *   <li>Run the main method and read the refactored trace first.
 *   <li>Then read the unrefactored trace immediately after it.
 *   <li>Ask which trace you would rather debug at 2 a.m. and which one tells you what the system is
 *       actually doing.
 * </ol>
 *
 * <p>INTENT: This is a tutorial about naming, not about Minecraft. Its purpose is to make the
 * connection between code vocabulary and trace quality impossible to miss.
 */
public class MinecraftExample {

  private static final Logger logger = LoggerFactory.getLogger(MinecraftExample.class);

  public static void main(String[] args) {
    var pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    var renderer = new IndentedTextRenderer();
    var mermaidRenderer = new MermaidSequenceDiagramRenderer();

    runRefactoredDemo(context, renderer, mermaidRenderer);
    context.reset();
    runUnrefactoredDemo(context, renderer);
  }

  private static void runRefactoredDemo(
      NarrativeContext context,
      IndentedTextRenderer renderer,
      MermaidSequenceDiagramRenderer mermaidRenderer) {
    logger.info("=== Refactored: Player Joins World ===\n");
    logger.info("  Domain-specific names make the trace self-documenting.\n");

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

    server.playerJoined("Steve");
    var trace = context.captureTrace();
    DemoTraces.capture("Refactored: Player Joins World", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));
    logger.info("\n--- Mermaid ---\n");
    logger.info("\n{}", mermaidRenderer.render(trace));
    logger.info("\n--- Sequence diagram (ASCII) ---\n");
    logger.info(
        "\n{}", AsciiSequenceDiagram.render(new PlantUmlSequenceDiagramRenderer().render(trace)));
  }

  private static void runUnrefactoredDemo(NarrativeContext context, IndentedTextRenderer renderer) {
    logger.info("\n=== Unrefactored: Player Joins World ===\n");
    logger.info("  Generic names — same behavior, but the trace tells you nothing.\n");

    var dataProcessor =
        NarrativeTraceProxy.trace(new DefaultDataProcessor(), DataProcessor.class, context);
    var stateManager =
        NarrativeTraceProxy.trace(new DefaultStateManager(), StateManager.class, context);
    var thingFactory =
        NarrativeTraceProxy.trace(new DefaultThingFactory(), ThingFactory.class, context);
    var entityHandler =
        NarrativeTraceProxy.trace(new DefaultEntityHandler(), EntityHandler.class, context);
    var manager =
        NarrativeTraceProxy.trace(
            new DefaultGameManager(dataProcessor, stateManager, thingFactory, entityHandler),
            GameManager.class,
            context);

    manager.handle("Steve");
    var trace = context.captureTrace();
    DemoTraces.capture("Unrefactored: Player Joins World", trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", renderer.render(trace));

    logger.info("\n  ^ Same call graph. Same return values. Only names differ.");
    logger.info("  If your code can't tell its own story, it needs refactoring.");
  }
}
