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
 * A test that fails on its own assertion while every traced call returns normally — the shape of a
 * run whose test code is not recorded, so no span carries the failure.
 */
public class AssertionFailureFixture {

  @Test
  public void pricingTotalDisagreesWithTheReferenceBasket() {
    Traced.call("Pricing", "total", "200");
    throw new AssertionError("total for the reference basket expected [999] but found [200]");
  }
}
