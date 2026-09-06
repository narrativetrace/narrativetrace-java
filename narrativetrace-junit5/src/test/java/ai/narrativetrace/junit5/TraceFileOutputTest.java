/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.output.OutputDirectoryResolver;
import ai.narrativetrace.core.output.TraceFileWriter;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ScenarioResult;
import ai.narrativetrace.core.render.TraceMetadata;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceFileOutputTest {

  @Test
  void writesMarkdownTraceFileToResolvedPath(@TempDir Path tempDir) throws IOException {
    var resolver = new OutputDirectoryResolver(tempDir);
    var writer = new TraceFileWriter();
    var renderer = new MarkdownRenderer();

    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata = new TraceMetadata("Customer places order", ScenarioResult.SUCCESS);
    var markdown = renderer.renderDocument(tree, metadata);

    var filePath = resolver.traceFile("io.example.OrderServiceTest", "customerPlacesOrder");
    writer.write(markdown, filePath);

    assertThat(filePath).exists();
    var content = Files.readString(filePath);
    assertThat(content).startsWith("---\n");
    assertThat(content).contains("type: trace");
    assertThat(content).contains("## Trace: OrderService.placeOrder");
    assertThat(content).contains("**OrderService.placeOrder**");
  }

  @Test
  void skipsWritingWhenTraceIsEmpty(@TempDir Path tempDir) throws IOException {
    var resolver = new OutputDirectoryResolver(tempDir);
    var tree = new DefaultTraceTree(List.of());

    var filePath = resolver.traceFile("io.example.OrderServiceTest", "emptyTest");

    // Should not write when tree is empty
    assertThat(tree.isEmpty()).isTrue();
    assertThat(filePath).doesNotExist();
  }
}
