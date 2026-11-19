/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A test that fails on its own assertion while every traced production call returns normally — the
 * shape of a run whose test code is not recorded, so no span carries the failure.
 *
 * <p>Runs only when {@link TestFailureInTraceFileTest} marks its request with {@link
 * #HARNESS_PARAMETER}; selected any other way it reports itself skipped, which keeps a deliberate
 * failure out of the module's own run without an exclusion in a build script.
 */
@ExtendWith(NarrativeTraceExtension.class)
@EnabledIf("drivenByHarness")
class AssertionFailureFixture {

  /** Launcher configuration parameter the driving test sets to {@code "true"}. */
  static final String HARNESS_PARAMETER = "narrativetrace.junit5.test.assertionFailureHarness";

  /** The production collaborator, reached through the proxy route. */
  interface Pricing {
    int total(int price, int quantity);
  }

  static boolean drivenByHarness(ExtensionContext context) {
    return context
        .getConfigurationParameter(HARNESS_PARAMETER)
        .map(Boolean::parseBoolean)
        .orElse(false);
  }

  @Test
  void pricingTotalDisagreesWithTheReferenceBasket(NarrativeContext context) {
    Pricing pricing = NarrativeTraceProxy.trace((p, q) -> p * q, Pricing.class, context);

    int actual = pricing.total(100, 2);

    assertEquals(999, actual, "total for the reference basket");
  }
}
