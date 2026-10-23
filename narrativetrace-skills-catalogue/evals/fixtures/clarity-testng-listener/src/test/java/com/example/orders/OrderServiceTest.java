/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

import static org.testng.Assert.assertEquals;

import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.testng.NarrativeTraceListener;
import org.testng.annotations.Test;

public class OrderServiceTest {

  @Test
  public void customerPlacesAnOrder() {
    var service =
        NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, NarrativeTraceListener.context());
    assertEquals(service.placeOrder("customer-42", "sku-7", 2), "ORD-customer-42-sku-7-2");
  }
}
