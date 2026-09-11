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

class MarkdownRendererTest {

  @Test
  void parentReturnRendersInlineOnTheEntryLineWithNoClosingRepeat() {
    var child =
        new TraceNode(
            new MethodSignature("PriceService", "lookup", List.of()),
            List.of(),
            new TraceOutcome.Returned("9.99"),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("CartService", "total", List.of()),
            List.of(child),
            new TraceOutcome.Returned("19.98"),
            2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("- **CartService.total**() → `19.98` — 2ms");
    assertThat(result).contains("  - **PriceService.lookup**() → `9.99` — 1ms");
    assertThat(result).doesNotContain("\n  - → ");
  }

  @Test
  void voidParentEndsAfterItsChildrenWithNoClosingLineAtAll() {
    var child =
        new TraceNode(
            new MethodSignature("TripLedger", "recordExpense", List.of()),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("TripSettlementService", "recordExpense", List.of()),
            List.of(child),
            new TraceOutcome.Returned(null),
            2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result)
        .isEqualTo(
            "- **TripSettlementService.recordExpense**() — 2ms\n"
                + "  - **TripLedger.recordExpense**() — 1ms");
  }

  @Test
  void narratedLeafMethodRendersItsNarration() {
    var node =
        new TraceNode(
            new MethodSignature(
                "TripSettlementService",
                "recordExpense",
                List.of(),
                "Recording Hotel (400.00 EUR)",
                null),
            List.of(),
            new TraceOutcome.Returned(null),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("*Recording Hotel (400.00 EUR)*");
  }

  @Test
  void parentWithNullOutcomeEndsAfterChildrenWithoutStrayBullet() {
    var child =
        new TraceNode(
            new MethodSignature("Svc", "step", List.of()),
            List.of(),
            new TraceOutcome.Returned("1"),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()), List.of(child), null, 2_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).isEqualTo("- **Svc.run**() — 2ms\n  - **Svc.step**() → `1` — 1ms");
  }

  @Test
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("- **ExpenseValidator.ensureValid**(expense: `\"e\"`) — 1ms");
    assertThat(result).doesNotContain("→");
    assertThat(result).doesNotContain("null");
  }

  @Test
  void rendersLeafCallWithBoldMethodInlineCodeValuesAndDuration() {
    var node =
        new TraceNode(
            new MethodSignature(
                "OrderValidator",
                "validate",
                List.of(new ParameterCapture("cartId", "\"CART-77\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"valid\""),
            2_000_000L // 2ms
            );
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result)
        .contains("- **OrderValidator.validate**(cartId: `\"CART-77\"`) → `\"valid\"` — 2ms");
  }

  @Test
  void rendersNestedCallsAsMarkdownList() {
    var child =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "reserve",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result)
        .contains(
            "- **OrderService.placeOrder**(customerId: `\"C-123\"`) → `\"order-42\"` — 412ms");
    assertThat(result)
        .contains("  - **InventoryService.reserve**(customerId: `\"C-123\"`) → `true` — 24ms");
    assertThat(result).doesNotContain("\n  - → ");
  }

  @Test
  void rendersExceptionInBlockquote() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(new ParameterCapture("amount", "97.14", false))),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")),
            203_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("> ❌ `IllegalStateException`: Card expired");
    assertThat(result)
        .as("a leaf exception renders as an inline blockquote, not a closing bullet")
        .doesNotContain("- ❌");
  }

  @Test
  void escapesHtmlInExceptionMessageToPreventInjection() {
    // Exception messages commonly embed user input; they are placed as raw markdown, so unescaped
    // HTML becomes active markup when the .md trace is viewed in a browser/markdown preview.
    var node =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("bad <img src=x onerror=alert(1)>")),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("<img");
    assertThat(result).contains("&lt;img src=x onerror=alert(1)&gt;");
  }

  @Test
  void rendersNullExceptionMessageAsLiteralNullWithoutError() {
    // An exception with no message must not NPE the escaper; the prior behavior rendered "null".
    var node =
        new TraceNode(
            new MethodSignature("Svc", "run", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException()),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("`RuntimeException`: null");
  }

  @Test
  void rendersRedactedParamsInInlineCode() {
    var node =
        new TraceNode(
            new MethodSignature(
                "AuthService",
                "login",
                List.of(
                    new ParameterCapture("username", "\"admin\"", false),
                    new ParameterCapture("password", "[REDACTED]", true))),
            List.of(),
            new TraceOutcome.Returned("true"),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("username: `\"admin\"`");
    assertThat(result).contains("password: `[REDACTED]`");
  }

  @Test
  void suppressedParamRendersEllipsisInsteadOfEmptyBackticks() {
    // At non-DETAIL levels the value is captured as the empty string; empty backticks read as a
    // rendering bug. Render a horizontal ellipsis at render time; the captured value stays "".
    var suppressed = new ParameterCapture("tripName", "", false).withoutValues();
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of(suppressed)),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("tripName: …");
    assertThat(result).doesNotContain("tripName: ``");
  }

  @Test
  void widensCodeFenceWhenParamValueContainsBacktickToPreventBreakout() {
    // A backtick in a value would close a single-backtick code span, letting following text become
    // active markup. The span must use a fence wider than any internal backtick run.
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of(new ParameterCapture("p", "x`y", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("p: `` x`y ``");
  }

  @Test
  void widensCodeFenceWhenInlineReturnValueContainsBacktickToPreventBreakout() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of()),
            List.of(),
            new TraceOutcome.Returned("a`b"),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("→ `` a`b ``");
  }

  @Test
  void widensCodeFenceWhenClosingReturnValueContainsBacktickToPreventBreakout() {
    // Parity: the closing (parent-with-children) return path must fence like the inline leaf path.
    var child =
        new TraceNode(
            new MethodSignature("Inner", "run", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("Svc", "m", List.of()),
            List.of(child),
            new TraceOutcome.Returned("a`b"),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("→ `` a`b ``");
  }

  @Test
  void rendersSlowThresholdMarker() {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentGateway",
                "charge",
                List.of(new ParameterCapture("amount", "242.95", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            340_000_000L // 340ms > 200ms threshold
            );
    var tree = new DefaultTraceTree(List.of(node));

    var renderer = new MarkdownRenderer(200); // 200ms threshold
    var result = renderer.render(tree);

    assertThat(result).contains("340ms");
    assertThat(result).contains("⚠️ slow");
  }

  @Test
  void doesNotMarkFastCallsAsSlow() {
    var node =
        new TraceNode(
            new MethodSignature("OrderValidator", "validate", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"valid\""),
            2_000_000L // 2ms
            );
    var tree = new DefaultTraceTree(List.of(node));

    var renderer = new MarkdownRenderer(200);
    var result = renderer.render(tree);

    assertThat(result).doesNotContain("⚠️");
  }

  @Test
  void rendersFullDocumentWithFrontmatterAndScenarioHeader() {
    var child =
        new TraceNode(
            new MethodSignature(
                "InventoryService",
                "reserve",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-123\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(root));
    var metadata = new TraceMetadata("Customer places order", ScenarioResult.SUCCESS);

    var result = new MarkdownRenderer().renderDocument(tree, metadata);

    assertThat(result).startsWith("---\n");
    assertThat(result).contains("type: trace");
    assertThat(result).contains("scenario: Customer places order");
    assertThat(result).contains("---\n\n## Trace: OrderService.placeOrder");
    assertThat(result).contains("**Scenario:** Customer places order");
    assertThat(result).contains("**Duration:** 412ms | **Result:** PASSED");
    assertThat(result).contains("### Call Flow");
    assertThat(result).contains("- **OrderService.placeOrder**");
  }

  @Test
  void escapesScenarioInDocumentBodyHeaderExactlyAsInFrontmatter() {
    // The frontmatter routes the scenario through YAML escaping; the body header used to append
    // it raw, so a hostile scenario forged document structure (a heading) and injected raw HTML
    // into the rendered Markdown. 2026-09-08 audit.
    var node =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(node));
    var metadata =
        new TraceMetadata(
            "ok\n# forged heading\n<img src=x onerror=alert(1)>", ScenarioResult.SUCCESS);

    var result = new MarkdownRenderer().renderDocument(tree, metadata);

    assertThat(result)
        .contains("**Scenario:** ok\\n# forged heading\\n&lt;img src=x onerror=alert(1)&gt;\n");
    assertThat(result.lines()).noneMatch("# forged heading"::equals);
    // The body (everything after the frontmatter block) carries no active HTML. The frontmatter
    // itself is YAML, where < and > are ordinary printable characters inside a quoted scalar.
    var body = result.substring(result.indexOf("## Trace:"));
    assertThat(body).doesNotContain("<img");
  }

  @Test
  void rendersNarrationAsItalicsBelowMethodEntry() {
    var child =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
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
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("*Placing order of 5 units for customer C-123*");
  }

  @Test
  void escapesHtmlInNarrationToPreventInjection() {
    // Narration is resolved from a template over param values, so it can carry user input.
    var child =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            1_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(), "narr <img src=x> end", null),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("<img");
    assertThat(result).contains("*narr &lt;img src=x&gt; end*");
  }

  @Test
  void renderDocumentWithEmptyTreeSkipsTraceHeader() {
    var tree = new DefaultTraceTree(List.of());
    var metadata = new TraceMetadata("Empty scenario", ScenarioResult.SUCCESS);

    var result = new MarkdownRenderer().renderDocument(tree, metadata);

    assertThat(result).contains("---");
    assertThat(result).doesNotContain("## Trace:");
    assertThat(result).doesNotContain("### Call Flow");
  }

  @Test
  void rendersParentNodeWithThrewOutcome() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")),
            5_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("❌ `RuntimeException`: order failed");
  }

  @Test
  void escapesHtmlInClosingExceptionMessageToPreventInjection() {
    // Parity: a parent (with children) renders its outcome via the closing path, which must escape
    // exactly like the inline leaf path.
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")),
            5_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("failed <script>alert(1)</script>")),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("<script>");
    assertThat(result).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
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
            new TraceOutcome.Threw(new IllegalStateException("Card expired")),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("Payment failed for amount 99.95");
  }

  @Test
  void escapesHtmlInErrorContextToPreventInjection() {
    // errorContext is resolved from a template over param values, so it can carry user input too.
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of(), null, "ctx <b>x</b>"),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("boom")),
            5_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("<b>");
    assertThat(result).contains("ctx &lt;b&gt;x&lt;/b&gt;");
  }

  @Test
  void rendersNestedLeafExceptionWithCorrectIndentation() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of(), null, "Payment failed"),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("Card expired")),
            5_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    // Child is at depth 1, so inline exception should be indented with 4 spaces (depth+1 = 2
    // repeats of "  ")
    assertThat(result).contains("\n    > ");
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("Order processing failed");
  }

  @Test
  void escapesHtmlInClosingErrorContextToPreventInjection() {
    // Parity: errorContext on a parent (closing path) must escape like the inline leaf path.
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")));
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of(), null, "ctx <i>y</i>"),
            List.of(child),
            new TraceOutcome.Threw(new RuntimeException("order failed")));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("<i>");
    assertThat(result).contains("ctx &lt;i&gt;y&lt;/i&gt;");
  }

  @Test
  void rendersLeafNodeWithZeroDurationWithoutDurationMarker() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "method", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            0L);
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).doesNotContain("ms");
  }

  @Test
  void sequentialChildrenRenderUnchangedWhenNoConcurrencyInfo() {
    var child1 =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var child2 =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            340_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"order-42\""),
            412_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("  - **InventoryService.reserve**() → `true` — 24ms");
    assertThat(result).contains("  - **PaymentService.charge**() → `\"ok\"` — 340ms");
    assertThat(result).doesNotContain("⑂");
    assertThat(result).doesNotContain("⑃");
    assertThat(result).doesNotContain("↦");
  }

  @Test
  void concurrentChildrenRenderInsideForkJoinMarkers() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⑂ fork");
    assertThat(result).contains("⑃ join");
  }

  @Test
  void forkHeaderShowsGroupIdSoInterleavedGroupsAreCorrelatable() {
    var info = new ConcurrencyInfo("fork-7", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⑂ fork [1 tasks] [groupId: fork-7]");
  }

  @Test
  void concurrentMembersPrefixedWithArrow() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("↦ **DiscountEngine.calculate**");
  }

  @Test
  void concurrentMembersShowThreadName() {
    var info = new ConcurrencyInfo("fork-1", "calc-pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("[thread: calc-pool-1]");
  }

  @Test
  void concurrentMemberOnVirtualThreadIsMarkedVirtual() {
    var info = new ConcurrencyInfo("fork-1", "vt-1", 100, true, ConcurrencyKind.FORK_JOIN);
    var child =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("[thread: vt-1 (virtual)]");
  }

  @Test
  void concurrentMemberOnPlatformThreadIsNotMarkedVirtual() {
    var info = new ConcurrencyInfo("fork-1", "pt-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("[thread: pt-1]");
    assertThat(result).doesNotContain("(virtual)");
  }

  @Test
  void concurrentMembersSortedByClassNameMethodName() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var childZ =
        new TraceNode(
            new MethodSignature("ZService", "alpha", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"z\""),
            10_000_000L,
            0L,
            info);
    var childA =
        new TraceNode(
            new MethodSignature("AService", "beta", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"a\""),
            20_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("Orchestrator", "run", List.of()),
            List.of(childZ, childA),
            new TraceOutcome.Returned("\"done\""),
            30_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    int posA = result.indexOf("AService.beta");
    int posZ = result.indexOf("ZService.alpha");
    assertThat(posA).isLessThan(posZ);
  }

  @Test
  void joinLineShowsWallTime() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child1 =
        new TraceNode(
            new MethodSignature("SlowService", "compute", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"slow\""),
            85_000_000L,
            0L,
            info);
    var child2 =
        new TraceNode(
            new MethodSignature("FastService", "lookup", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"fast\""),
            40_000_000L,
            0L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("Orchestrator", "run", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⑃ join — 85ms");
  }

  @Test
  void joinLineShowsWaitAnalysis() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var slow =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var fast =
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
            List.of(slow, fast),
            new TraceOutcome.Returned("\"done\""),
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("waited 45ms for DiscountEngine after LoyaltyService");
  }

  @Test
  void mixedSequentialAndConcurrentRenderInCorrectOrder() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var seqBefore =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            24_000_000L);
    var concurrent1 =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            0L,
            info);
    var concurrent2 =
        new TraceNode(
            new MethodSignature("LoyaltyService", "checkTier", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"GOLD\""),
            40_000_000L,
            0L,
            info);
    var seqAfter =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            340_000_000L);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(seqBefore, concurrent1, concurrent2, seqAfter),
            new TraceOutcome.Returned("\"order-42\""),
            500_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    int posReserve = result.indexOf("InventoryService.reserve");
    int posFork = result.indexOf("⑂ fork");
    int posJoin = result.indexOf("⑃ join");
    int posCharge = result.indexOf("PaymentService.charge");
    assertThat(posReserve).isLessThan(posFork);
    assertThat(posFork).isLessThan(posJoin);
    assertThat(posJoin).isLessThan(posCharge);
  }

  @Test
  void multipleGroupsRenderAsSeparateForkJoinBlocks() {
    var info1 = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var info2 = new ConcurrencyInfo("fork-2", "pool-2", 101, false, ConcurrencyKind.FORK_JOIN);
    var g1child =
        new TraceNode(
            new MethodSignature("SvcA", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"a\""),
            10_000_000L,
            0L,
            info1);
    var g2child =
        new TraceNode(
            new MethodSignature("SvcB", "work", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"b\""),
            20_000_000L,
            0L,
            info2);
    var parent =
        new TraceNode(
            new MethodSignature("Parent", "run", List.of()),
            List.of(g1child, g2child),
            new TraceOutcome.Returned("\"done\""),
            30_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    // Should have two separate fork/join blocks
    int firstFork = result.indexOf("⑂ fork");
    int secondFork = result.indexOf("⑂ fork", firstFork + 1);
    assertThat(firstFork).isGreaterThanOrEqualTo(0);
    assertThat(secondFork).isGreaterThan(firstFork);
  }

  @Test
  void nestedConcurrencyRendersCorrectly() {
    var innerInfo =
        new ConcurrencyInfo("fork-inner", "inner-1", 200, false, ConcurrencyKind.FORK_JOIN);
    var innerChild =
        new TraceNode(
            new MethodSignature("SubTask", "compute", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"sub\""),
            5_000_000L,
            0L,
            innerInfo);
    var outerInfo =
        new ConcurrencyInfo("fork-outer", "outer-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var outerChild =
        new TraceNode(
            new MethodSignature("Worker", "process", List.of()),
            List.of(innerChild),
            new TraceOutcome.Returned("\"result\""),
            20_000_000L,
            0L,
            outerInfo);
    var parent =
        new TraceNode(
            new MethodSignature("Orchestrator", "run", List.of()),
            List.of(outerChild),
            new TraceOutcome.Returned("\"done\""),
            30_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    // Outer fork/join
    assertThat(result).contains("⑂ fork [1 tasks]");
    assertThat(result).contains("↦ **Worker.process**");
    // Inner fork/join (nested)
    int outerFork = result.indexOf("⑂ fork");
    int innerFork = result.indexOf("⑂ fork", outerFork + 1);
    assertThat(innerFork).isGreaterThan(outerFork);
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⤳ fire-and-forget");
    assertThat(result).contains("NotifySvc.send");
    assertThat(result).doesNotContain("⑂ fork");
    assertThat(result).doesNotContain("⑃ join");
  }

  @Test
  void fireAndForgetMarkerShowsGroupId() {
    var launcherInfo =
        new ConcurrencyInfo("fanf-9", "notify-1", 200, false, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "notifyAsync", List.of()),
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
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⤳ fire-and-forget [groupId: fanf-9]");
  }

  @Test
  void fireAndForgetOnVirtualThreadIsMarkedVirtual() {
    var launcherInfo =
        new ConcurrencyInfo("fanf-3", "vt-notify", 200, true, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher =
        new TraceNode(
            new MethodSignature("Parent", "notifyAsync", List.of()),
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
            100_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("[thread: vt-notify (virtual)]");
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⤳ fire-and-forget");
    assertThat(result).contains("[launched, result not captured]");
    assertThat(result).doesNotContain("NotifySvc");
  }

  @Test
  void multipleFireAndForgetGroupsRenderIndependently() {
    var info1 =
        new ConcurrencyInfo("fanf-1", "notify-1", 200, false, ConcurrencyKind.FIRE_AND_FORGET);
    var info2 =
        new ConcurrencyInfo("fanf-2", "audit-1", 201, false, ConcurrencyKind.FIRE_AND_FORGET);
    var launcher1 =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(),
            null,
            0L,
            0L,
            info1);
    var launcher2 =
        new TraceNode(
            new MethodSignature("Parent", "fire-and-forget", List.of()),
            List.of(),
            null,
            0L,
            0L,
            info2);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(launcher1, launcher2),
            new TraceOutcome.Returned("\"done\""),
            50_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    var matches = result.split("⤳ fire-and-forget");
    assertThat(matches).hasSize(3); // text before + 2 occurrences
  }

  @Test
  void sequentialAsyncMembersShowAwaitedSequentiallyAnnotation() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child1 =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            1_000L,
            info);
    var child2 =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            124_000_000L,
            100_000_000L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"order-42\""),
            300_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("[async, awaited sequentially]");
  }

  @Test
  void sequentialAsyncRendererShowsOptimizationHint() {
    var info = new ConcurrencyInfo("fork-1", "pool-1", 100, false, ConcurrencyKind.FORK_JOIN);
    var child1 =
        new TraceNode(
            new MethodSignature("DiscountEngine", "calculate", List.of()),
            List.of(),
            new TraceOutcome.Returned("0.15"),
            85_000_000L,
            1_000L,
            info);
    var child2 =
        new TraceNode(
            new MethodSignature("InventoryService", "reserve", List.of()),
            List.of(),
            new TraceOutcome.Returned("true"),
            124_000_000L,
            100_000_000L,
            info);
    var parent =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child1, child2),
            new TraceOutcome.Returned("\"order-42\""),
            300_000_000L);
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⚡ Sequential async: total 209ms, parallelizable to ~124ms");
  }

  @Test
  void rendersIncompleteOutcomeAsInFlightMarker() {
    var node =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Incomplete());
    var tree = new DefaultTraceTree(List.of(node));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("⏳ in-flight");
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

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("Recursive.call0");
    assertThat(result).contains("Recursive.call1000");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeRendersWithACycleMarkerAndNeverHangs() {
    var childHolder = new ArrayList<TraceNode>();
    var b = leaf("b", childHolder);
    var a = leaf("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("Recursive.a");
    assertThat(result).contains("Recursive.b");
    assertThat(result).contains("… (cycle)");
  }

  /**
   * A folded run whose first iteration hits the cycle bound: the {@code ×k more …} summary must
   * still print (via {@link MarkdownRenderer}'s onLimit path), not silently vanish. Every iteration
   * is an isomorphic {@code a -> b -> (cycle)} pair — built from independent node instances so the
   * run's three iterations are structurally identical, which is what makes them fold at all.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aFoldedRunWhoseFirstIterationIsCyclicStillPrintsTheSummary() {
    var parent =
        new TraceNode(
            new MethodSignature("Trip", "settle", List.of()),
            List.of(cyclicPair(), cyclicPair(), cyclicPair()),
            new TraceOutcome.Returned(null));
    var tree = new DefaultTraceTree(List.of(parent));

    var result = new MarkdownRenderer().render(tree);

    assertThat(result).contains("… (cycle)");
    assertThat(result).contains("more");
  }

  private static TraceNode cyclicPair() {
    var childHolder = new ArrayList<TraceNode>();
    var b = leaf("b", childHolder);
    var a = leaf("a", List.of(b));
    childHolder.add(a);
    return a;
  }
}
