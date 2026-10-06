/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/orders/RedactionTest.java
package com.example.orders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A parameter named for what it holds is hidden by that NAME alone — no annotation, no
 * configuration. This is the proof the doctor's {@code trap.redaction-proof} check looks for.
 *
 * <p><b>@llmNote</b> Nothing here is annotated, on purpose: an {@code @NotTraced} anywhere below
 * would prove the annotation works and say nothing about the deny-list. Plain JUnit assertions for
 * the same kind of reason — the install block this listing ships with declares {@code
 * junit-jupiter} and nothing else, so it has to compile as pasted.
 */
@ExtendWith(NarrativeTraceExtension.class)
class RedactionTest {

  interface PaymentService {
    String charge(String customerId, String paymentToken);
  }

  @Test
  void aDenyListedParameterIsRedactedInTheTrace(NarrativeContext context) {
    PaymentService payments =
        NarrativeTraceProxy.trace(
            (customerId, paymentToken) -> "PAY-" + customerId, PaymentService.class, context);

    payments.charge("C-1234", "tok_live_9a8b7c6d5e4f");

    String rendered = new IndentedTextRenderer().render(context.captureTrace());
    assertTrue(rendered.contains("[REDACTED]"), rendered);
    assertFalse(rendered.contains("tok_live_9a8b7c6d5e4f"), rendered);
    assertTrue(rendered.contains("C-1234"), rendered);
  }
}
