/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

public class OrderProcessor {

  private final Calculator calculator = new Calculator();

  public int processOrder(int price, int quantity) {
    return calculator.add(price, quantity);
  }
}
