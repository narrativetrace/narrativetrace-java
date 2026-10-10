/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// The smallest test that drives the real path: the collaborators the change touched, wired as
// production wires them, each wrapped with the test's NarrativeContext. After the test, the
// extension writes the shape to build/narrativetrace/structural/<TestClass>/<scenario>.nt and the
// values to build/narrativetrace/traces/<TestClass>/<scenario>.md.
@ExtendWith(NarrativeTraceExtension.class)
class PlaceOrderFlowTest {

  @Test
  void customer_places_an_order(NarrativeContext context) {
    OrderService orders =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    var orderId = orders.placeOrder("C-1234", "SKU-KB", 2);

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
  }
}
