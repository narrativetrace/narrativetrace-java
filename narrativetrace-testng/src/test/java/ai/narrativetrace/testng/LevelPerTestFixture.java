/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/**
 * Two ordered tests sharing the JVM's one context, where the first changes {@code
 * narrativetrace.level} for the one after it — read back by {@link LevelPerTestTest}.
 */
public class LevelPerTestFixture {

  static volatile boolean firstCapturedParameterValues;
  static volatile boolean secondCapturedParameterValues;

  @Test(priority = 1)
  public void first() {
    firstCapturedParameterValues = NarrativeTraceListener.context().capturesParameterValues();
    System.setProperty("narrativetrace.level", "SUMMARY");
  }

  @Test(priority = 2)
  public void second() {
    secondCapturedParameterValues = NarrativeTraceListener.context().capturesParameterValues();
  }
}
