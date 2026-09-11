/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/orders/Main.java
package com.example.orders;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.proxy.NarrativeTraceProxy;

public class Main {
  public static void main(String[] args) {
    var context = new ThreadLocalNarrativeContext();
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    service.placeOrder("C-1234", "SKU-KB", 2);

    System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
    context.reset();
  }
}
