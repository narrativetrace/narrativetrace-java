/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {

  // snippet:begin fixedTraceparent
  // A fixed W3C traceparent, adopted so this page's embedded output always names the same trace.
  // A real run generates a random one every time (never this — it is this DEMO's own constant,
  // not the library default) via the same mechanism a filter uses for an inbound request header.
  static final String DEMO_TRACEPARENT = "00-a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4-a1b2c3d4a1b2c3d4-01";

  // snippet:end fixedTraceparent

  public static void main(String[] args) {
    var context = new ThreadLocalNarrativeContext();
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    service.placeOrder("C-1234", "SKU-KB", 2);

    System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
    context.reset();
  }
}
