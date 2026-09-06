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

class MermaidSequenceDiagramRendererTest {

  private final MermaidSequenceDiagramRenderer renderer = new MermaidSequenceDiagramRenderer();

  private static TraceOutcome.Returned collectionOf(String typeName, int count) {
    var elements = new java.util.ArrayList<RenderedValue>();
    for (int i = 0; i < count; i++) {
      elements.add(new RenderedValue.ObjectVal(typeName, Map.of()));
    }
    return new TraceOutcome.Returned(
        "[huge rendered collection payload...]", new RenderedValue.ListVal(elements));
  }

  @Test
  void summarizesCollectionReturnAsCountPlusNoun() {
    var node =
        new TraceNode(
            new MethodSignature("Ledger", "expensesOf", List.of()),
            List.of(),
            collectionOf("Transfer", 3),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("Ledger-->>Ledger: 3 transfers");
    assertThat(diagram).doesNotContain("huge rendered collection payload");
  }

  @Test
  void renderWithAliasesSummarizesCollectionReturnAsCountPlusNoun() {
    var node =
        new TraceNode(
            new MethodSignature("Ledger", "expensesOf", List.of()),
            List.of(),
            collectionOf("Transfer", 3),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("-->>").contains("3 transfers");
    assertThat(diagram).doesNotContain("huge rendered collection payload");
  }

  @Test
  void truncatesLongScalarReturnSoDiagramStaysRenderable() {
    var huge = "\"" + "x".repeat(1000) + "\"";
    var node =
        new TraceNode(
            new MethodSignature("Report", "dump", List.of()),
            List.of(),
            new TraceOutcome.Returned(huge, new RenderedValue.StringVal(huge)),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    var returnLine = diagram.lines().filter(l -> l.contains("-->>")).findFirst().orElseThrow();
    assertThat(returnLine).endsWith("…");
    assertThat(returnLine.length()).isLessThan(120);
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

    assertThat(diagram).contains("AuditSink-->>AuditSink: \u2713");
    assertThat(diagram).doesNotContain("null");
  }

  @Test
  void rendersSingleCallAsParticipantMessageAndReply() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "calculateTotal",
                List.of(new ParameterCapture("orderId", "\"O-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("99.0"),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).startsWith("sequenceDiagram");
    assertThat(diagram).contains("participant OrderService");
    assertThat(diagram).contains("OrderService->>OrderService: calculateTotal(orderId)");
    assertThat(diagram).contains("OrderService-->>OrderService: 99.0");
  }

  @Test
  void rendersNestedCallsWithCorrectParticipantOrderingAndFlow() {
    var inventoryCall =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "reserveStock",
                List.of(new ParameterCapture("sku", "\"SKU-1\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"),
            5_000_000L);
    var paymentCall =
        new TraceNode(
            new MethodSignature(
                "PaymentService", "charge", List.of(new ParameterCapture("amount", "99.0", false))),
            List.of(),
            new TraceOutcome.Returned("\"TX-456\""),
            8_000_000L);
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("orderId", "\"O-123\"", false))),
            List.of(inventoryCall, paymentCall),
            new TraceOutcome.Returned("\"OK\""),
            20_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.render(tree);

    // Participants appear in order of first encounter
    assertThat(diagram).contains("participant OrderService");
    assertThat(diagram).contains("participant InventoryService");
    assertThat(diagram).contains("participant PaymentService");
    // Nested calls go from parent to child
    assertThat(diagram).contains("OrderService->>OrderService: placeOrder(orderId)");
    assertThat(diagram).contains("OrderService->>InventoryService: reserveStock(sku)");
    assertThat(diagram).contains("InventoryService-->>OrderService: true");
    assertThat(diagram).contains("OrderService->>PaymentService: charge(amount)");
    assertThat(diagram).contains("PaymentService-->>OrderService: \"TX-456\"");
    assertThat(diagram).contains("OrderService-->>OrderService: \"OK\"");
  }

  @Test
  void rendersExceptionPathsWithCrossNotation() {
    var failingCall =
        new TraceNode(
            new MethodSignature(
                "PaymentService", "charge", List.of(new ParameterCapture("amount", "99.0", false))),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("Insufficient funds")),
            3_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(failingCall),
            new TraceOutcome.Threw(new RuntimeException("Insufficient funds")),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("PaymentService-xOrderService: RuntimeException");
    assertThat(diagram).contains("OrderService-xOrderService: RuntimeException");
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

    assertThat(diagram).contains("OrderService-->>OrderService: null");
  }

  @Test
  void renderWithAliasesHandlesSingleUpperCaseCharClassName() {
    var node =
        new TraceNode(
            new MethodSignature("A", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("participant A as A");
  }

  @Test
  void rendersWithAliasesUsingTwoLetterAbbreviations() {
    var child =
        new TraceNode(
            new MethodSignature("InventoryService", "checkStock", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"OK\""),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("participant OS as OrderService");
    assertThat(diagram).contains("participant IS as InventoryService");
    assertThat(diagram).contains("OS->>OS: placeOrder()");
    assertThat(diagram).contains("OS->>IS: checkStock()");
    assertThat(diagram).contains("IS-->>OS: true");
  }

  @Test
  void renderWithAliasesHandlesClassNameWithNoUppercaseLetters() {
    var node =
        new TraceNode(
            new MethodSignature("scheduler", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    // Should not produce "participant  as scheduler" (empty alias)
    assertThat(diagram).doesNotContain("participant  as");
    assertThat(diagram).contains("participant scheduler as scheduler");
  }

  @Test
  void renderWithAliasesDeduplicatesCollidingAliases() {
    var child =
        new TraceNode(
            new MethodSignature("OutdoorService", "check", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "process", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"OK\""),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.renderWithAliases(tree);

    // Both would produce "OS" — they must get distinct aliases
    var lines = diagram.lines().filter(l -> l.trim().startsWith("participant")).toList();
    var aliases = lines.stream().map(l -> l.trim().split(" ")[1]).toList();
    assertThat(aliases).doesNotHaveDuplicates();
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
        .contains("\"com.example.OrderService\"->>\"com.example.OrderService\": placeOrder()");
  }

  @Test
  void renderWithAliasesRendersExceptionAndMultipleParams() {
    var child =
        new TraceNode(
            new MethodSignature(
                "PaymentService",
                "charge",
                List.of(
                    new ParameterCapture("amount", "99.0", false),
                    new ParameterCapture("currency", "\"USD\"", false))),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("fail")),
            1_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("fail")),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("OS->>PS: charge(amount, currency)");
    assertThat(diagram).contains("PS-xOS: RuntimeException");
    assertThat(diagram).contains("OS-xOS: RuntimeException");
  }

  @Test
  void rendersIncompleteOutcomeAsNoteOver() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Incomplete(),
            0L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("Note over OrderService: in-flight");
    assertThat(diagram).doesNotContain("-->>"); // no return arrow
  }

  @Test
  void renderWithAliasesRendersIncompleteOutcomeAsNoteOver() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Incomplete(),
            0L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("Note over OS: in-flight");
  }

  @Test
  void renderWithAliasesShouldQuoteParticipantDisplayNamesThatNeedEscaping() {
    var node =
        new TraceNode(
            new MethodSignature("com.example.Order Service", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"OK\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("participant OS as \"com.example.Order Service\"");
  }

  @Test
  void neutralizesNewlineInReturnValueToPreventDiagramLineInjection() {
    // A returned String whose rendered value embeds a newline followed by a Mermaid directive.
    // ValueRenderer preserves '\n', so raw interpolation would terminate the message line and
    // start a new line the Mermaid engine parses as a `click` interaction binding.
    var node =
        new TraceNode(
            new MethodSignature("ServiceA", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\n    click ServiceA evilCallback\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.render(tree);

    // No output line may begin with the injected directive: newlines in message text are
    // neutralized.
    assertThat(diagram.lines()).noneMatch(line -> line.stripLeading().startsWith("click"));
  }

  @Test
  void renderWithAliasesNeutralizesNewlineInReturnValueToPreventLineInjection() {
    // Parity: the aliased render path must sanitize message text exactly like render().
    var node =
        new TraceNode(
            new MethodSignature("ServiceA", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\n    click ServiceA evilCallback\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram.lines()).noneMatch(line -> line.stripLeading().startsWith("click"));
  }

  @Test
  void aliasCollisionWithMultipleSuffixes() {
    // Three classes whose uppercase abbreviation is "OS": OrderService, OtherService, OnlineService
    var grandchild =
        new TraceNode(
            new MethodSignature("OnlineService", "check", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"));
    var child =
        new TraceNode(
            new MethodSignature("OtherService", "validate", List.of()),
            List.of(grandchild),
            new TraceOutcome.Returned("true"));
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "process", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(root));

    var diagram = renderer.renderWithAliases(tree);

    // Should have three distinct aliases: OS, OS2, OS3
    assertThat(diagram).contains("participant OS as OrderService");
    assertThat(diagram).contains("participant OS2 as OtherService");
    assertThat(diagram).contains("participant OS3 as OnlineService");
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

    assertThat(diagram).startsWith("sequenceDiagram");
    assertThat(diagram).contains("Recursive->>Recursive: call0()");
  }

  @Test
  void aCyclicCallTreeRendersEveryNodeOnceWithACycleMarkerAndNeverHangs() {
    var childHolder = new java.util.ArrayList<TraceNode>();
    var b = leaf("Ring", "b", childHolder);
    var a = leaf("Ring", "a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var diagram = renderer.render(tree);

    assertThat(diagram).contains("Ring->>Ring: a()");
    assertThat(diagram).contains("Ring->>Ring: b()");
    assertThat(diagram).contains("Note over Ring: … (cycle)");
  }

  @Test
  void aCyclicCallTreeRendersWithAliasesWithoutHanging() {
    var childHolder = new java.util.ArrayList<TraceNode>();
    var b = leaf("Ring", "b", childHolder);
    var a = leaf("Ring", "a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var diagram = renderer.renderWithAliases(tree);

    assertThat(diagram).contains("participant R as Ring");
    assertThat(diagram).contains("Note over R: … (cycle)");
  }
}
