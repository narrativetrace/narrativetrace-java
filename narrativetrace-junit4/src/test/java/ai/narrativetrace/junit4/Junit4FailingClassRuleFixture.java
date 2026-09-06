/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.junit.Assert.fail;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A suite using both rules where the class statement itself throws.
 *
 * <p>INTENT: A {@code @ClassRule} wraps the statement that already includes {@code @AfterClass}, so
 * a teardown that throws is what makes {@code base.evaluate()} throw — and that is what used to
 * skip the class-level report. One failing test sits beside a passing one so the aggregate report
 * has both outcomes to aggregate, which is the state a triage session is actually in.
 */
public class Junit4FailingClassRuleFixture {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void customerPlacesOrder() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    narrativeTrace.context().exitMethodWithReturn("order-1");
  }

  @Test
  public void customerPaymentIsDeclined() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    narrativeTrace.context().exitMethodWithReturn("declined");
    fail("deliberate test failure");
  }

  @AfterClass
  public static void teardownFails() {
    throw new IllegalStateException("deliberate class-level failure");
  }
}
