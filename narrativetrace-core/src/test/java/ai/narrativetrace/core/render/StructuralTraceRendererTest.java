/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The AI-safe structural trace artifact (ADR-002): the developer-authored shape of a scenario —
 * class/method/parameter names, call hierarchy, outcome kinds — with zero runtime values, zero
 * timings, zero identifiers. Deterministic by construction so the artifact is byte-diffable
 * (approval baselines, conformance fixtures) and safe to hand to an AI agent.
 */
class StructuralTraceRendererTest {

  private final StructuralTraceRenderer renderer = new StructuralTraceRenderer();

  @Test
  void voidCallRendersNoOutcomeKind() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AuditSink", "record", List.of(new ParameterCapture("entry", "\"e\"", false))),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    assertThat(renderer.render(tree)).isEqualTo("- AuditSink.record(entry)\n");
  }

  @Test
  void thrownExceptionRendersTypeButNeverTheMessage() {
    var node =
        new TraceNode(
            new MethodSignature("PaymentGateway", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("card 4111-1111 declined")),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result).isEqualTo("- PaymentGateway.charge() !! IllegalStateException\n");
    assertThat(result).doesNotContain("4111");
  }

  @Test
  void childCallsNestByIndentationInCaptureOrder() {
    var validate =
        new TraceNode(
            new MethodSignature(
                "ExpenseValidator",
                "ensureValid",
                List.of(new ParameterCapture("expense", "\"e\"", false))),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var store =
        new TraceNode(
            new MethodSignature("TripLedger", "recordExpense", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature(
                "TripSettlementService",
                "recordExpense",
                List.of(new ParameterCapture("tripName", "\"Ski\"", false))),
            List.of(validate, store),
            new TraceOutcome.Returned(null),
            2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    assertThat(renderer.render(tree))
        .isEqualTo(
            """
            - TripSettlementService.recordExpense(tripName)
              - ExpenseValidator.ensureValid(expense)
              - TripLedger.recordExpense()
            """);
  }

  @Test
  void incompleteCallRendersInFlightMarker() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "hang", List.of()),
            List.of(),
            new TraceOutcome.Incomplete(),
            0L);
    var tree = new DefaultTraceTree(List.of(node));

    assertThat(renderer.render(tree)).isEqualTo("- Svc.hang() ?? incomplete\n");
  }

  @Test
  void identicalBehaviorRendersByteIdenticalOutputRegardlessOfValuesAndTimings() {
    var first =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-1\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-1\""),
            10_000_000L);
    var second =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-99999\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-2\""),
            999_000_000L);

    var a = renderer.render(new DefaultTraceTree(List.of(first)));
    var b = renderer.render(new DefaultTraceTree(List.of(second)));

    assertThat(a).isEqualTo(b);
  }

  @Test
  void forkMembersRenderSortedBySignatureWithoutThreadIdentity() {
    var onThreadTwo =
        new ConcurrencyInfo("fork-1", "pool-1-thread-2", 22, false, ConcurrencyKind.FORK_JOIN);
    var onThreadOne =
        new ConcurrencyInfo("fork-1", "pool-1-thread-1", 21, false, ConcurrencyKind.FORK_JOIN);
    var stock =
        new TraceNode(
            new MethodSignature("StockService", "check", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            5_000_000L,
            0L,
            onThreadTwo);
    var discount =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            9_000_000L,
            0L,
            onThreadOne);
    var parent =
        new TraceNode(
            new MethodSignature("CheckoutService", "quote", List.of()),
            List.of(stock, discount), // capture order: nondeterministic across threads
            new TraceOutcome.Returned("\"quote\""),
            20_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            """
            - CheckoutService.quote() → value
              ~ fork [2]
                - DiscountEngine.calculate() → value
                - StockService.check() → value
            """);
    assertThat(result).doesNotContain("pool-1");
  }

  @Test
  void fireAndForgetLaunchRendersMarkerWithChildren() {
    var info = new ConcurrencyInfo("faf-1", "pool-9", 9, false, ConcurrencyKind.FIRE_AND_FORGET);
    var work =
        new TraceNode(
            new MethodSignature("NotificationService", "send", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var launcher =
        new TraceNode(
            new MethodSignature("NotificationService", "launch", List.of()),
            List.of(work),
            null,
            0L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("CheckoutService", "complete", List.of()),
            List.of(launcher),
            new TraceOutcome.Returned(null),
            2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    assertThat(renderer.render(tree))
        .isEqualTo(
            """
            - CheckoutService.complete()
              ~ fire-and-forget
                - NotificationService.send()
            """);
  }

  @Test
  void documentFormCarriesOnlyTheStableScenarioHeader() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.renderDocument(tree, "Weekend trip settles with three transfers");

    assertThat(result)
        .isEqualTo(
            """
            scenario: Weekend trip settles with three transfers

            - Svc.run()
            """);
  }

  @Test
  void leafCallRendersNamesAndOutcomeKindWithoutValues() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(
                    new ParameterCapture("customerId", "\"C-123\"", false),
                    new ParameterCapture("quantity", "2", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result).isEqualTo("- OrderService.placeOrder(customerId, quantity) → value\n");
    assertThat(result).doesNotContain("C-123");
    assertThat(result).doesNotContain("412");
  }

  private static TraceNode leaf(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  /** Depth 1,000: every line's indent grows with depth, so total output is quadratic by design. */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepCallTreeRendersWithoutStackOverflow() {
    TraceNode current = leaf("call1000", List.of());
    for (var i = 0; i < 1_000; i++) {
      current = leaf("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var result = renderer.render(tree);

    assertThat(result).contains("Recursive.call0(");
    assertThat(result).contains("Recursive.call1000(");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeRendersWithACycleMarkerAndNeverHangs() {
    var childHolder = new ArrayList<TraceNode>();
    var b = leaf("b", childHolder);
    var a = leaf("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var result = renderer.render(tree);

    assertThat(result).contains("Recursive.a(");
    assertThat(result).contains("Recursive.b(");
    assertThat(result).contains("… (cycle)");
  }
}
