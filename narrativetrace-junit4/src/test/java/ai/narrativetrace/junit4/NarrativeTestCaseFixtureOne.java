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
 * First of two {@link NarrativeTestCase} subclasses used to verify the inherited static {@link
 * NarrativeTraceClassRule} is safe to share (see {@link NarrativeTestCaseInheritanceTest}).
 */
public class NarrativeTestCaseFixtureOne extends NarrativeTestCase {

  @Test
  public void customerPlacesOrder() {
    context().enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context().exitMethodWithReturn("order-1");
  }

  @Test
  public void customerCancelsOrder() {
    context().enterMethod(new MethodSignature("OrderService", "cancelOrder", List.of()));
    context().exitMethodWithReturn("cancelled");
  }
}
