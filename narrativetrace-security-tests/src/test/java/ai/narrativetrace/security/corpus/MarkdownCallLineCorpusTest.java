/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code feedback.json} Markdown call-line row to the bytes the real renderer writes.
 *
 * <p>INTENT: the row's value is what reaches a clipboard, so it must be generated, not typed — a
 * hand-typed line can drift from the renderer's emphasis and the gate would be tested against a
 * line no runtime emits.
 */
class MarkdownCallLineCorpusTest {

  private static final String ROW_ID = "rendered-call-markdown-bold-name";

  @Test
  void theMarkdownRowIsALineTheRealRendererEmits() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(
                    new ParameterCapture("customerId", "\"C-1234\"", false),
                    new ParameterCapture("total", "19.99", false))),
            List.of(),
            new TraceOutcome.Returned("\"ORD-9001\""),
            1_000_000L);

    String rendered = new MarkdownRenderer().render(new DefaultTraceTree(List.of(node)));

    var row = HostileCorpus.feedbacks().stream().filter(c -> c.id().equals(ROW_ID)).findFirst();
    assertThat(row).as("corpus row %s", ROW_ID).isPresent();
    assertThat(rendered).contains(row.get().value());
  }
}
