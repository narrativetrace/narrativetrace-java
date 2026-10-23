/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Two failing tests among three passing ones — the shape of a real triage run, for the pointer and
 * index tests. Excluded from the module's own test task: its failures are the point. The {@code
 * FailingTestFixture} suffix is what keeps PIT's own test-class scan from running it as a top-level
 * test (build.gradle.kts {@code excludedTestClasses}).
 */
@ExtendWith(NarrativeTraceExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class MixedOutcomeFailingTestFixture {

  @Test
  void aPassesFirst(NarrativeContext context) {
    traced(context, "OrderService", "placeOrder", "order-1");
  }

  @Test
  void bFailsOnStock(NarrativeContext context) {
    context.enterMethod(new MethodSignature("StockService", "reserve", List.of()));
    context.exitMethodWithException(new IllegalStateException("only 0 left, wanted 3"), null);
    throw new AssertionError("stock check | failed\nsecond line");
  }

  @Test
  void cPassesSecond(NarrativeContext context) {
    traced(context, "OrderService", "cancelOrder", "cancelled");
  }

  @Test
  void dFailsOnPayment(NarrativeContext context) {
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new IllegalArgumentException("card declined"), null);
    throw new AssertionError("payment was declined");
  }

  @Test
  void ePassesThird(NarrativeContext context) {
    traced(context, "OrderService", "listOrders", "[]");
  }

  private static void traced(
      NarrativeContext context, String className, String methodName, String result) {
    context.enterMethod(new MethodSignature(className, methodName, List.of()));
    context.exitMethodWithReturn(result);
  }
}
