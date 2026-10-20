/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Two ordered tests sharing one class-owned context, where the first changes {@code
 * narrativetrace.level} for the one after it — read back by {@link LevelPerTestJunit4Test}.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LevelPerTestJunit4Fixture {

  static boolean firstCapturedParameterValues;
  static boolean secondCapturedParameterValues;

  @ClassRule public static final NarrativeTraceClassRule CLASS_RULE = new NarrativeTraceClassRule();

  @Rule public final NarrativeTraceRule rule = CLASS_RULE.testRule();

  @Test
  public void aFirst() {
    firstCapturedParameterValues = rule.context().capturesParameterValues();
    System.setProperty("narrativetrace.level", "SUMMARY");
  }

  @Test
  public void bSecond() {
    secondCapturedParameterValues = rule.context().capturesParameterValues();
  }
}
