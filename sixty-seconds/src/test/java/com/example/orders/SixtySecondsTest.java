/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.orders;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
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
 *
 * <p><b>@llmNote</b> Adopts {@link Main#DEMO_TRACEPARENT} — the same fixed id {@code Main} adopts —
 * so the console renderer's {@code trace: ... (...)} header (2026-09-13 ruling, item 4) is the same
 * stable phrase every run, exactly like the rest of this capture; no {@code mask=traceName} needed
 * on this one embed, unlike every other live-output snippet in the docs.
 */
@ExtendWith(NarrativeTraceExtension.class)
class SixtySecondsTest {

  @Test
  void see_a_trace(NarrativeContext context) throws IOException {
    context.adoptTraceparent(Traceparent.parse(Main.DEMO_TRACEPARENT));
    OrderService service =
        NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);

    var orderId = service.placeOrder("C-1234", "SKU-KB", 2);

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");

    var rendered = new IndentedTextRenderer().render(context.captureTrace());
    var traceHeader = "trace: " + TraceNamer.name(context.traceId().value());
    assertThat(rendered).startsWith(traceHeader);
    assertThat(rendered)
        .matches(
            Pattern.quote(traceHeader)
                + " \\([0-9a-f]{7}\\)\n\n"
                + "OrderService\\.placeOrder\\(customerId: \"C-1234\", productId: \"SKU-KB\", "
                + "quantity: 2\\) → \"ORD-C-1234-SKU-KB-2\" — \\d+ms");

    var consoleCapture = Path.of("build/narrativetrace/sixty-seconds/see-a-trace.txt");
    Files.createDirectories(consoleCapture.getParent());
    Files.writeString(consoleCapture, rendered + System.lineSeparator());
  }
}
