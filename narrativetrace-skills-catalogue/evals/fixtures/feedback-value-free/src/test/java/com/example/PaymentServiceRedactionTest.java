/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * This project's redaction proof, and it passes: the deny-listed parameter is hidden, the ordinary
 * one survives.
 *
 * <p>It asserts the marker through the library's OWN public constant, {@link
 * RedactionPolicy#MARKER}, rather than retyping its text — so the value a renderer writes and the
 * value this test expects cannot drift apart. That is the whole reason to reference a constant.
 */
@ExtendWith(NarrativeTraceExtension.class)
class PaymentServiceRedactionTest {

  @Test
  void theAuthTokenIsRedactedAndTheOtherArgumentsSurvive(NarrativeContext context) {
    PaymentService payments =
        NarrativeTraceProxy.trace(new DefaultPaymentService(), PaymentService.class, context);

    payments.charge("C-1234", "tok_live_9a8b7c6d5e4f", "42.00");

    String rendered = new IndentedTextRenderer().render(context.captureTrace());
    assertTrue(rendered.contains(RedactionPolicy.MARKER), rendered);
    assertFalse(rendered.contains("tok_live_9a8b7c6d5e4f"), rendered);
    assertTrue(rendered.contains("C-1234"), rendered);
  }
}
