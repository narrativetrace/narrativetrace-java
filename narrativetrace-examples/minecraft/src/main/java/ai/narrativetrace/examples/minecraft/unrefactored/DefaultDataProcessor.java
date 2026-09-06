/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft.unrefactored;

public class DefaultDataProcessor implements DataProcessor {

  @Override
  public DataResult process(int a, int b) {
    return new DataResult(a, b, "plains");
  }
}
