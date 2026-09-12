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
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/sixty-seconds.md's "See a trace in 60 seconds" tutorial: the exact call
 * {@code Main} makes, run through the real {@link NarrativeTraceProxy}, produces the narrative the
 * page shows (rule 8, docs as tests).
 *
 * <p>{@code @ExtendWith(NarrativeTraceExtension.class)} is the runtime's own JUnit 5 test
 * integration — its {@code afterTestExecution} writes this test's trace artifact under {@code
 * build/narrativetrace/traces/SixtySecondsTest/} unconditionally (output on by default, ruled
 * 2026-09-11). That artifact frames the scenario name above the rendered call, so it is not what
 * the page embeds: the page's "3. Run it" output block is the bare line {@link Main} prints, and
 * this test saves that exact rendering to its own file for {@code snippetCheck}/{@code snippetSync}
 * to embed, byte-stable but for the duration (masked by {@code mask=duration}).
 */
@ExtendWith(NarrativeTraceExtension.class)
class SixtySecondsTest {

  @Test
  void see_a_trace(NarrativeContext context) throws IOException {
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    var orderId = service.placeOrder("C-1234", "SKU-KB", 2);

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");

    var rendered = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(rendered)
        .matches(
            "OrderService\\.placeOrder\\(customerId: \"C-1234\", productId: \"SKU-KB\", "
                + "quantity: 2\\) → \"ORD-C-1234-SKU-KB-2\" — \\d+ms");

    var consoleCapture = Path.of("build/narrativetrace/sixty-seconds/see-a-trace.txt");
    Files.createDirectories(consoleCapture.getParent());
    Files.writeString(consoleCapture, rendered + System.lineSeparator());
  }
}
