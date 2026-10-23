/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng.sample;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.testng.NarrativeTraceListener;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * The sample suite Gradle's own {@code useTestNG()} runs (the {@code testngSample} task): no
 * {@code @Listeners}, no configuration — the listener arrives by service loader, as it does for an
 * adopter. One test fails on purpose; its pointer and the index are what the sample shows.
 */
public class SampleCheckoutTest {

  @DataProvider
  public Object[][] coupons() {
    return new Object[][] {{"SPRING"}, {"SUMMER"}};
  }

  @Test(priority = 1)
  public void cartIsPriced() {
    traced("PricingService", "price", "42.00");
  }

  @Test(priority = 2, dataProvider = "coupons")
  public void couponIsApplied(String coupon) {
    traced("CouponService", "apply", coupon);
  }

  @Test(priority = 3)
  public void paymentIsCaptured() {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature("PaymentService", "capture", List.of()));
    context.exitMethodWithException(new IllegalStateException("card expired"), null);
    throw new AssertionError("the payment was not captured");
  }

  private static void traced(String service, String method, String result) {
    var context = NarrativeTraceListener.context();
    context.enterMethod(new MethodSignature(service, method, List.of()));
    context.exitMethodWithReturn(result);
  }
}
