/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FrontmatterBuilderTest {

  @Test
  void buildsYamlFrontmatterFromTraceTree() {
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

    var frontmatter = new FrontmatterBuilder().scenario("Customer places order").build(tree);

    assertThat(frontmatter).startsWith("---\n");
    assertThat(frontmatter).endsWith("---\n");
    assertThat(frontmatter).contains("type: trace");
    assertThat(frontmatter).contains("scenario: Customer places order");
    assertThat(frontmatter).contains("entry_point: OrderService.placeOrder");
    assertThat(frontmatter).contains("duration_ms: 412");
    assertThat(frontmatter).contains("method_count: 2");
    assertThat(frontmatter).contains("error_count: 0");
  }

  @Test
  void emptyTreeProducesZeroMethodAndErrorCounts() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().build(tree);

    assertThat(frontmatter).contains("method_count: 0");
    assertThat(frontmatter).contains("error_count: 0");
    assertThat(frontmatter).doesNotContain("entry_point");
    assertThat(frontmatter).doesNotContain("scenario");
  }

  @Test
  void treeWithErrorsCountsAllErrors() {
    var failChild =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("declined")),
            5_000_000L);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(failChild),
            new TraceOutcome.Threw(new RuntimeException("order failed")),
            10_000_000L);
    var tree = new DefaultTraceTree(List.of(root));

    var frontmatter = new FrontmatterBuilder().scenario("Order fails").build(tree);

    assertThat(frontmatter).contains("error_count: 2");
    assertThat(frontmatter).contains("method_count: 2");
  }

  @Test
  void scenarioWithColonIsYamlQuoted() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("Test: edge case").build(tree);

    assertThat(frontmatter).contains("scenario: \"Test: edge case\"");
  }

  @Test
  void scenarioWithHashIsYamlQuoted() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("Test #1").build(tree);

    assertThat(frontmatter).contains("scenario: \"Test #1\"");
  }

  @Test
  void scenarioWithQuoteIsEscaped() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("Test \"quoted\" name").build(tree);

    assertThat(frontmatter).contains("scenario: \"Test \\\"quoted\\\" name\"");
  }

  @Test
  void scenarioWithNewlineIsEscaped() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("Line one\nLine two").build(tree);

    assertThat(frontmatter).contains("scenario: \"Line one\\nLine two\"");
    // Must not contain a raw newline inside the scenario value
    assertThat(frontmatter).doesNotContain("scenario: \"Line one\nLine two\"");
  }

  @Test
  void scenarioWithBackslashIsEscaped() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("path\\to\\file").build(tree);

    assertThat(frontmatter).contains("scenario: \"path\\\\to\\\\file\"");
  }

  @Test
  void supplementaryCharacterIsEscapedNotPassedThroughRaw() {
    // A raw astral-plane character in the frontmatter can land its high-surrogate half exactly
    // on a downstream parser's read-buffer boundary: SnakeYAML 2.3 reads 1024-char chunks and,
    // when a chunk ends on a high surrogate, reads one char past its own buffer —
    // IndexOutOfBoundsException while parsing spec-valid YAML (2026-09-08 audit;
    // nightly fuzz crashes 2026-09-03..08, one class). Emitting the \U escape keeps the
    // frontmatter BMP-only, so no boundary can ever split a pair, and a conforming parser
    // decodes it back to the same code point — fidelity preserved.
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("see🙈no evil").build(tree);

    assertThat(frontmatter).contains("scenario: \"see\\U0001f648no evil\"");
    assertThat(frontmatter.chars().anyMatch(c -> Character.isSurrogate((char) c))).isFalse();
  }

  @Test
  void aLongAstralRunLeavesNoSurrogateAtAnyParserBufferBoundary() {
    // The minimized shape of the fuzz crash class: enough astral pairs that char index 1023 of
    // the frontmatter block is a high surrogate. After escaping, no output character is a
    // surrogate at all, so the property holds for every alignment, not just this one.
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("🙈".repeat(600)).build(tree);

    assertThat(frontmatter.chars().anyMatch(c -> Character.isSurrogate((char) c))).isFalse();
  }

  @Test
  void plainScenarioIsNotQuoted() {
    var tree = new DefaultTraceTree(List.of());

    var frontmatter = new FrontmatterBuilder().scenario("Customer places order").build(tree);

    assertThat(frontmatter).contains("scenario: Customer places order");
    assertThat(frontmatter).doesNotContain("scenario: \"");
  }

  @Test
  void includesTraceNameAndIdWhenSpanContextPresent() {
    var sc = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    var root =
        new TraceNode(
            new MethodSignature("Svc", "handle", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            100_000L,
            0L,
            null,
            sc);
    var tree = new DefaultTraceTree(List.of(root));

    var frontmatter = new FrontmatterBuilder().build(tree);

    assertThat(frontmatter).contains("trace_id: " + sc.traceId().value());
    assertThat(frontmatter).contains("trace_name: " + TraceNamer.name(sc.traceId().value()));
  }

  /**
   * One node whose own children list is mutable, so a test can wire up a genuine reference cycle.
   */
  private static TraceNode mutableNode(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("null"),
        1_000_000L);
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepCallTreeCountsWithoutStackOverflow() {
    TraceNode current = mutableNode("call5000", List.of());
    for (var i = 0; i < 5_000; i++) {
      current = mutableNode("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var frontmatter = new FrontmatterBuilder().build(tree);

    assertThat(frontmatter).contains("method_count: 5001");
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicCallTreeCountsWithoutHanging() {
    var childHolder = new java.util.ArrayList<TraceNode>();
    var b = mutableNode("b", childHolder);
    var a = mutableNode("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var frontmatter = new FrontmatterBuilder().build(tree);

    // a, b, and a again where the walk stops instead of re-descending into the cycle — the same
    // "still counts as itself" convention every TreeWalk-bounded walker in this codebase shares.
    assertThat(frontmatter).contains("method_count: 3");
  }
}
