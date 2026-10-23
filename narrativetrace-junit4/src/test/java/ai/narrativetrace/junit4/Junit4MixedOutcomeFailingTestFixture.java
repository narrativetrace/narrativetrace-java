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
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Two failing tests among three passing ones, under both rules — the shape of a real triage run.
 * Excluded from the module's own test task: its failures are the point.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Junit4MixedOutcomeFailingTestFixture {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void aPassesFirst() {
    traced("OrderService", "placeOrder", "order-1");
  }

  @Test
  public void bFailsOnStock() {
    narrativeTrace.context().enterMethod(new MethodSignature("StockService", "reserve", List.of()));
    narrativeTrace
        .context()
        .exitMethodWithException(new IllegalStateException("only 0 left, wanted 3"), null);
    throw new AssertionError("stock check | failed\nsecond line");
  }

  @Test
  public void cPassesSecond() {
    traced("OrderService", "cancelOrder", "cancelled");
  }

  @Test
  public void dFailsOnPayment() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    narrativeTrace
        .context()
        .exitMethodWithException(new IllegalArgumentException("card declined"), null);
    throw new AssertionError("payment was declined");
  }

  @Test
  public void ePassesThird() {
    traced("OrderService", "listOrders", "[]");
  }

  private void traced(String className, String methodName, String result) {
    narrativeTrace.context().enterMethod(new MethodSignature(className, methodName, List.of()));
    narrativeTrace.context().exitMethodWithReturn(result);
  }
}
