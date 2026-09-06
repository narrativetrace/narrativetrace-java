/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

public class Calculator {

  public int add(int a, int b) {
    return a + b;
  }

  public int divide(int a, int b) {
    return a / b;
  }

  public double wideParams(long x, double y, int z) {
    return x + y + z;
  }

  public static int staticAdd(int a, int b) {
    return a + b;
  }
}
