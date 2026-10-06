/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

import static org.junit.Assert.assertEquals;

import ai.narrativetrace.junit4.NarrativeTraceRule;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.Rule;
import org.junit.Test;

public class OrderServiceTest {

  @Rule public NarrativeTraceRule narrativeTrace = new NarrativeTraceRule();

  @Test
  public void customerPlacesAnOrder() {
    var service =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, narrativeTrace.context());
    assertEquals("ORD-customer-42-sku-7-2", service.placeOrder("customer-42", "sku-7", 2));
  }
}
