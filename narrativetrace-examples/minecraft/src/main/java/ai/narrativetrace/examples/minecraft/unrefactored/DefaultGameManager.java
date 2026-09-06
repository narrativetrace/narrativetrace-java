/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.unrefactored;

public class DefaultGameManager implements GameManager {

  private final DataProcessor dataProcessor;
  private final StateManager stateManager;
  private final ThingFactory thingFactory;
  private final EntityHandler entityHandler;

  public DefaultGameManager(
      DataProcessor dataProcessor,
      StateManager stateManager,
      ThingFactory thingFactory,
      EntityHandler entityHandler) {
    this.dataProcessor = dataProcessor;
    this.stateManager = stateManager;
    this.thingFactory = thingFactory;
    this.entityHandler = entityHandler;
  }

  @Override
  public String handle(String input) {
    dataProcessor.process(0, 0);
    stateManager.update(1, 4);
    stateManager.update(2, 8);
    int item = thingFactory.create(1);
    stateManager.update(item, 1);
    entityHandler.execute(1, 10, 64, 20);
    return input + " joined the world";
  }
}
