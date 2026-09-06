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

class IndentedTextRendererTest {

  @org.junit.jupiter.api.Test
  void narratedLeafMethodRendersItsNarration() {
    var node =
        new TraceNode(
            new MethodSignature(
                "TripSettlementService", "recordExpense", List.of(), "Recording Hotel", null),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    org.assertj.core.api.Assertions.assertThat(result).contains("// Recording Hotel");
  }

  @org.junit.jupiter.api.Test
  void redactedParameterRendersMarkerNotItsCapturedValue() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AccountService",
                "login",
                List.of(new ParameterCapture("password", "\"hunter2\"", true))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    org.assertj.core.api.Assertions.assertThat(result).contains("password: [REDACTED]");
    org.assertj.core.api.Assertions.assertThat(result).doesNotContain("hunter2");
  }

  @org.junit.jupiter.api.Test
  void voidMethodRendersNoOutcomeInsteadOfNull() {
    var node =
        new TraceNode(
            new MethodSignature(
                "ExpenseValidator",
                "ensureValid",
                List.of(new ParameterCapture("expense", "\"e\"", false))),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    org.assertj.core.api.Assertions.assertThat(result).doesNotContain("null");
    org.assertj.core.api.Assertions.assertThat(result).doesNotContain("\u2192");
  }

  @Test
  void rendersSingleLeafCall() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""));
    var tree = new DefaultTraceTree(List.of(node));

    var renderer = new IndentedTextRenderer();
    var result = renderer.render(tree);

    assertThat(result).isEqualTo("OrderService.placeOrder(customerId: \"C-123\") → \"order-42\"");
  }

  @Test
  void rendersNestedCallsWithIndentation() {
    var child =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "checkStock",
                List.of(new ParameterCapture("itemId", "\"ITEM-1\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"));
    var parent =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result)
        .isEqualTo(
            """
            OrderService.placeOrder(customerId: "C-123")
            ├── InventoryService.checkStock(itemId: "ITEM-1") → true
            └── → "order-42\"\
            """);
  }

  @Test
  void rendersRedactedParamsAsRedacted() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AuthService",
                "login",
                List.of(
                    new ParameterCapture("username", "\"admin\"", false),
                    new ParameterCapture("password", "[REDACTED]", true))),
            List.of(),
            new TraceOutcome.Returned("true"));
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result)
        .isEqualTo("AuthService.login(username: \"admin\", password: [REDACTED]) → true");
  }

  @Test
  void rendersDurationWhenPresent() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            24_000_000L // 24ms
            );
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result)
        .isEqualTo("OrderService.placeOrder(customerId: \"C-123\") → \"order-42\" — 24ms");
  }

  @Test
  void rendersNarrationBelowMethodEntry() {
    var child =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "reserve",
                List.of(
                    new ParameterCapture("customerId", "\"C-123\"", false),
                    new ParameterCapture("quantity", "5", false))),
            List.of(),
            new TraceOutcome.Returned("true"));
    var parent =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(
                    new ParameterCapture("customerId", "\"C-123\"", false),
                    new ParameterCapture("quantity", "5", false)),
                "Placing order of 5 units for customer C-123",
                null),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("OrderService.placeOrder(customerId: \"C-123\", quantity: 5)");
    assertThat(result).contains("│   // Placing order of 5 units for customer C-123");
    assertThat(result).contains("├── InventoryService.reserve");
  }

  @Test
  void rendersExceptionOutcome() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentService",
                "charge",
                List.of(new ParameterCapture("amount", "99.95", false))),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("insufficient funds")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result)
        .isEqualTo(
            "PaymentService.charge(amount: 99.95) !! IllegalStateException: insufficient funds");
  }

  @Test
  void sanitizesControlCharactersInInlineExceptionMessage() {
    // This renderer prints to System.out; a raw newline in the message forges a console line.
    var node =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("boom\nforged")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("boom\\nforged");
    assertThat(result.lines()).noneMatch(l -> l.equals("forged"));
  }

  @Test
  void sanitizesControlCharactersInClosingExceptionMessage() {
    var child =
        new TraceNode(
            new MethodSignature("Inner", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"));
    var parent =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("boom\nforged")));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("boom\\nforged");
    assertThat(result.lines()).noneMatch(l -> l.equals("forged"));
  }

  @Test
  void sanitizesControlCharactersInInlineErrorContext() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of(), null, "ctx\nforged"),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("boom")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("ctx\\nforged");
    assertThat(result.lines()).noneMatch(l -> l.equals("forged"));
  }

  @Test
  void sanitizesControlCharactersInClosingErrorContext() {
    var child =
        new TraceNode(
            new MethodSignature("Inner", "run", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")));
    var parent =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of(), null, "ctx\nforged"),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("ctx\\nforged");
    assertThat(result.lines()).noneMatch(l -> l.equals("forged"));
  }

  @Test
  void sanitizesControlCharactersInNarration() {
    var child =
        new TraceNode(
            new MethodSignature("Inner", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"));
    var parent =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of(), "narr\nforged", null),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("narr\\nforged");
    assertThat(result.lines()).noneMatch(l -> l.equals("forged"));
  }

  @Test
  void rendersErrorContextForLeafNodeWithThrewOutcome() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentService",
                "charge",
                List.of(new ParameterCapture("amount", "99.95", false)),
                null,
                "Payment failed for amount 99.95"),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("Payment failed for amount 99.95");
  }

  @Test
  void rendersErrorContextInClosingOutcome() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")));
    var parent =
        new TraceNode(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(), null, "Order processing failed"),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("!! RuntimeException: order failed | Order processing failed");
  }

  @Test
  void rendersParentNodeWithThrewOutcome() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")));
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("!! RuntimeException: order failed");
  }

  @Test
  void rendersConcurrentGroupWithForkJoinMarkers() {
    var info = new ConcurrencyInfo("fork-1", "calc-pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child1 =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var child2 =
        new TraceNode(
            new MethodSignature("LoyaltyService", "checkTier", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"GOLD\""),
            40_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("⑂ fork [2 tasks]");
    assertThat(result).contains("↦ DiscountEngine.calculate");
    assertThat(result).contains("↦ LoyaltyService.checkTier");
    assertThat(result).contains("[thread: calc-pool-1]");
    assertThat(result).contains("⑃ join — 85ms");
  }

  @Test
  void fireAndForgetLauncherWithChildrenRendersWithArrowMarker() {
    var launcherInfo =
        new ConcurrencyInfo("fanf-1", "notify-1", 200, false, ConcurrencyKind.FIRE_AND_FORGET);
    var childNode =
        new TraceNode(
            new MethodSignature("NotifySvc", "send", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            89_000_000L);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(childNode),
            null,
            0L,
            0L,
            launcherInfo);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(launcher),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("⤳ fire-and-forget");
    assertThat(result).contains("NotifySvc.send");
    assertThat(result).doesNotContain("⑂ fork");
  }

  @Test
  void sequentialAsyncMembersShowAwaitedSequentiallyAnnotation() {
    var infoA = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var childA =
        new TraceNode(
            new MethodSignature("SvcA", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            100_000_000L,
            1_000L,
            infoA);
    var childB =
        new TraceNode(
            new MethodSignature("SvcB", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            80_000_000L,
            200_000_000L,
            infoA);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(childA, childB),
            new TraceOutcome.Returned("\"done\""),
            300_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("[async, awaited sequentially]");
    assertThat(result).contains("⚡ Sequential async: total 180ms, parallelizable to ~100ms");
  }

  @Test
  void fireAndForgetLauncherWithNoChildrenShowsNotCapturedMessage() {
    var launcherInfo =
        new ConcurrencyInfo("fanf-2", "main", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(),
            null,
            0L,
            0L,
            launcherInfo);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(launcher),
            new TraceOutcome.Returned("\"done\""),
            50_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("⤳ fire-and-forget");
    assertThat(result).contains("[launched, result not captured]");
  }

  private static TraceNode leaf(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }

  /**
   * Depth 1,000, not the 5,000 other renderers use: every line here carries an indentation prefix
   * proportional to its depth (box-drawing characters), so total output is quadratic in depth by
   * design — a property of this renderer's format, not of the depth bound. 1,000 is still deep
   * enough to prove the bound machinery works, without an unrelated multi-hundred-MB allocation.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepCallTreeRendersWithoutStackOverflow() {
    TraceNode current = leaf("call1000", List.of());
    for (var i = 0; i < 1_000; i++) {
      current = leaf("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var result = new IndentedTextRenderer().render(tree);

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

    var result = new IndentedTextRenderer().render(tree);

    assertThat(result).contains("Recursive.a(");
    assertThat(result).contains("Recursive.b(");
    assertThat(result).contains("… (cycle)");
  }
}
