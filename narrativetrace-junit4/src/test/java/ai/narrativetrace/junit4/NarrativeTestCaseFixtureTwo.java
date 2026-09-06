/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.Test;

/**
 * Second of two {@link NarrativeTestCase} subclasses used to verify the inherited static {@link
 * NarrativeTraceClassRule} is safe to share (see {@link NarrativeTestCaseInheritanceTest}).
 */
public class NarrativeTestCaseFixtureTwo extends NarrativeTestCase {

  @Test
  public void customerChecksInventory() {
    context().enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    context().exitMethodWithReturn("in-stock");
  }
}
