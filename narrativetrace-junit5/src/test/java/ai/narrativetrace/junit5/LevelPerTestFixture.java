/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.core.context.NarrativeContext;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Two ordered tests sharing one suite-owned context, where the first changes {@code
 * narrativetrace.level} for the one after it — read back by {@link LevelPerTestFixtureTest}.
 * Changing a system property rather than passing an explicit launcher configuration parameter:
 * {@link NarrativeTraceExtension}'s {@code beforeEach} reads {@code
 * ExtensionContext.getConfigurationParameter}, which JUnit Platform resolves against system
 * properties when the launcher request names none explicitly — the only way one test can change
 * what the next sees without a second nested launch.
 */
@ExtendWith(NarrativeTraceExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LevelPerTestFixture {

  static boolean firstCapturedParameterValues;
  static boolean secondCapturedParameterValues;

  @Test
  @Order(1)
  void first(NarrativeContext context) {
    firstCapturedParameterValues = context.capturesParameterValues();
    System.setProperty("narrativetrace.level", "SUMMARY");
  }

  @Test
  @Order(2)
  void second(NarrativeContext context) {
    secondCapturedParameterValues = context.capturesParameterValues();
  }
}
