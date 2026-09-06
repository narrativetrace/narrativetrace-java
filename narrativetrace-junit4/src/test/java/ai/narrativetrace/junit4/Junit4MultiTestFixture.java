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
import org.junit.Rule;
import org.junit.Test;

public class Junit4MultiTestFixture {

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
  public void customerCancelsOrder() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("OrderService", "cancelOrder", List.of()));
    narrativeTrace.context().exitMethodWithReturn("cancelled");
  }
}
