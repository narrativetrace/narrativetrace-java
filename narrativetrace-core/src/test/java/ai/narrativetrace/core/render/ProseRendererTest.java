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

class ProseRendererTest {

  private final ProseRenderer renderer = new ProseRenderer();

  @Test
  void renders_simple_leaf_with_return() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo("The order service place order for customerId: \"C-123\", returning \"ok\".");
  }

  @Test
  void renders_leaf_with_multiple_params() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(
                    new ParameterCapture("customerId", "\"C-123\"", false),
                    new ParameterCapture("amount", "242.95", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            "The payment gateway charge for customerId: \"C-123\" amount: 242.95, returning"
                + " \"ok\".");
  }

  @Test
  void renders_leaf_with_no_params() {
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result).isEqualTo("The order service place order, returning \"ok\".");
  }

  @Test
  void renders_redacted_params() {
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

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            "The auth service login for username: \"admin\" password: [REDACTED], returning true.");
  }

  @Test
  void renders_complex_return_value() {
    var node =
        new TraceNode(
            new MethodSignature("PricingEngine", "calculateTotal", List.of()),
            List.of(),
            new TraceOutcome.Returned("[\"item1\", \"item2\"]"));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo("The pricing engine calculate total, returning [\"item1\", \"item2\"].");
  }

  @Test
  void renders_narration_when_present() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false)),
                "Placing order of 5 units for customer C-123",
                null),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            "The order service place order — Placing order of 5 units for customer C-123, returning"
                + " \"ok\".");
  }

  @Test
  void renders_narration_with_children() {
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
                List.of(new ParameterCapture("customerId", "\"C-123\"", false)),
                "Placing order of 5 units for customer C-123",
                null),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            """
            The order service place order — Placing order of 5 units for customer C-123:
              The inventory service check stock for itemId: "ITEM-1", returning true.
              Returned "ok".\
            """);
  }

  @Test
  void renders_parent_with_children() {
    var child1 =
        new TraceNode(
            new MethodSignature(
                "OrderValidator",
                "validateCart",
                List.of(new ParameterCapture("cartId", "\"CART-77\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"));
    var child2 =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(new ParameterCapture("amount", "242.95", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var parent =
        new TraceNode(
            new MethodSignature("OrderController", "submitOrder", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"done\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            """
            The order controller submit order:
              The order validator validate cart for cartId: "CART-77", returning true.
              The payment gateway charge for amount: 242.95, returning "ok".
              Returned "done".\
            """);
  }

  @Test
  void renders_nested_tree() {
    var grandchild =
        new TraceNode(
            new MethodSignature(
                "DiscountService",
                "findDiscounts",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"10%\""));
    var child =
        new TraceNode(
            new MethodSignature(
                "PricingEngine",
                "calculateTotal",
                List.of(new ParameterCapture("cartId", "\"CART-77\"", false))),
            List.of(grandchild),
            new TraceOutcome.Returned("242.95"));
    var parent =
        new TraceNode(
            new MethodSignature("OrderController", "submitOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            """
            The order controller submit order:
              The pricing engine calculate total for cartId: "CART-77":
                The discount service find discounts for customerId: "C-123", returning "10%".
                Returned 242.95.
              Returned "done".\
            """);
  }

  @Test
  void renders_multiple_roots() {
    var root1 =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));
    var root2 =
        new TraceNode(
            new MethodSignature("NotificationService", "sendEmail", List.of()),
            List.of(),
            new TraceOutcome.Returned(null));
    var tree = new DefaultTraceTree(List.of(root1, root2));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            """
            The order service place order, returning "ok".
            The notification service send email.\
            """);
  }

  @Test
  void renders_error_leaf() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(
                    new ParameterCapture("customerId", "\"C-456\"", false),
                    new ParameterCapture("amount", "97.14", false))),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            "The payment gateway failed to charge for customerId: \"C-456\" amount: 97.14 —"
                + " IllegalStateException: Card expired.");
  }

  @Test
  void renders_error_with_error_context() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(new ParameterCapture("amount", "99.95", false)),
                null,
                "Payment failed for amount 99.95"),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result)
        .isEqualTo(
            "The payment gateway failed to charge for amount: 99.95 — IllegalStateException: Card"
                + " expired (Payment failed for amount 99.95).");
  }

  @Test
  void renders_parent_with_threw_outcome_in_closing() {
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

    var result = renderer.render(tree);

    assertThat(result).contains("RuntimeException: order failed.");
  }

  @Test
  void renders_leaf_with_void_return() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned(null));
    var tree = new DefaultTraceTree(List.of(node));

    var result = renderer.render(tree);

    assertThat(result).isEqualTo("The order service place order for customerId: \"C-123\".");
  }

  @Test
  void rendersConcurrentGroupInProseForm() {
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

    var result = renderer.render(tree);

    assertThat(result).contains("Concurrently");
    assertThat(result).contains("discount engine calculate");
    assertThat(result).contains("loyalty service check tier");
  }

  @Test
  void fireAndForgetLauncherWithChildrenRendersInProseForm() {
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

    var result = renderer.render(tree);

    assertThat(result).contains("In the background");
    assertThat(result).contains("notify svc send");
    assertThat(result).doesNotContain("Concurrently");
  }

  @Test
  void sequentialAsyncGroupShowsOptimizationHintInProse() {
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

    var result = renderer.render(tree);

    assertThat(result)
        .contains("Note: tasks ran sequentially — total 180ms, parallelizable to ~100ms.");
  }

  @Test
  void fireAndForgetLauncherWithNoChildrenShowsProseNotCaptured() {
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

    var result = renderer.render(tree);

    assertThat(result).contains("In the background");
    assertThat(result).contains("launched, result not captured");
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

    var result = new ProseRenderer().render(tree);

    assertThat(result).contains("call0");
    assertThat(result).contains("call1000");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeRendersWithACycleMarkerAndNeverHangs() {
    var childHolder = new ArrayList<TraceNode>();
    var b = leaf("b", childHolder);
    var a = leaf("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var result = new ProseRenderer().render(tree);

    assertThat(result).contains("… (cycle)");
  }

  /**
   * CWE-117 in a shipped free-tier format: this renderer interpolated {@code getMessage()} with no
   * escaping at all while both its siblings folded it, and prose is nothing but lines, so one
   * newline in a message a validator echoed from user input wrote a sentence of the attacker's
   * choosing into the narrative.
   */
  @Test
  void anExceptionMessageCannotForgeALineInTheProseNarrative() {
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(
                new IllegalStateException("declined\nThe audit service approved payment.")));

    var rendered = renderer.render(new DefaultTraceTree(List.of(node)));

    assertThat(rendered.split("\n", -1)).as("one node is one sentence: %s", rendered).hasSize(1);
    assertThat(rendered).contains("declined\\nThe audit service approved payment.");
  }

  @Test
  void aCredentialShapedExceptionMessageIsRedactedInTheProseNarrative() {
    var node =
        new TraceNode(
            new MethodSignature("TokenService", "issue", List.of()),
            List.of(),
            new TraceOutcome.Threw(
                new IllegalStateException("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.c2lnbmF0dXJl")));

    var rendered = renderer.render(new DefaultTraceTree(List.of(node)));

    assertThat(rendered).contains("[REDACTED]").doesNotContain("eyJhbGciOiJIUzI1NiJ9");
  }
}
