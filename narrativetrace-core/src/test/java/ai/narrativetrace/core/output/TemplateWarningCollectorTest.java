/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

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

class TemplateWarningCollectorTest {

  @Test
  void returnsEmptyForTraceWithNoNarrations() {
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).isEmpty();
  }

  @Test
  void returnsEmptyForFullyResolvedNarration() {
    var sig =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("id", "\"C-123\"", false)),
            "Placing order for C-123",
            null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).isEmpty();
  }

  @Test
  void detectsUnresolvedPlaceholderInNarration() {
    var sig =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), "Placing order for {custmerId}", null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).className()).isEqualTo("OrderService");
    assertThat(warnings.get(0).methodName()).isEqualTo("placeOrder");
    assertThat(warnings.get(0).placeholder()).isEqualTo("custmerId");
    assertThat(warnings.get(0).field()).isEqualTo("narration");
  }

  @Test
  void detectsUnresolvedPlaceholderInErrorContext() {
    var sig =
        new MethodSignature("OrderService", "placeOrder", List.of(), null, "Failed for {orderId}");
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).field()).isEqualTo("errorContext");
    assertThat(warnings.get(0).placeholder()).isEqualTo("orderId");
  }

  @Test
  void detectsUnresolvedInChildNodes() {
    var childSig =
        new MethodSignature("InventoryService", "reserve", List.of(), "Reserving {sku}", null);
    var child = new TraceNode(childSig, List.of(), new TraceOutcome.Returned("true"));
    var parentSig = new MethodSignature("OrderService", "placeOrder", List.of());
    var parent = new TraceNode(parentSig, List.of(child), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(parent));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).className()).isEqualTo("InventoryService");
    assertThat(warnings.get(0).placeholder()).isEqualTo("sku");
  }

  @Test
  void formatRendersReadableOutput() {
    var warnings =
        List.of(
            new TemplateWarningCollector.TemplateWarning(
                "OrderService", "placeOrder", "custmerId", "narration"));

    var output = TemplateWarningCollector.format(warnings);

    assertThat(output).contains("Unresolved template placeholder");
    assertThat(output).contains("OrderService.placeOrder");
    assertThat(output).contains("{custmerId}");
    assertThat(output).contains("narration");
  }

  @Test
  void formatReturnsEmptyStringForNoWarnings() {
    assertThat(TemplateWarningCollector.format(List.of())).isEmpty();
  }

  @Test
  void flagsMultiLevelPropertyPathAsUnsupportedNested() {
    // {expense.payer.name} can never resolve — the parser supports one property level
    // (object.property), so "payer.name" is treated as a single, missing accessor and the
    // placeholder survives literally. This is a structural authoring error, distinct from a
    // single-level path that merely saw null data at runtime.
    var sig =
        new MethodSignature(
            "ExpenseService", "pay", List.of(), "Paying {expense.payer.name}", null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var warnings = TemplateWarningCollector.collect(tree);
    var output = TemplateWarningCollector.format(warnings);

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).placeholder()).isEqualTo("expense.payer.name");
    assertThat(output).contains("{expense.payer.name}");
    assertThat(output).contains("nested path not supported");
  }

  @Test
  void singleLevelUnresolvedPathIsNotFlaggedAsNested() {
    // {expense.payer} is a supported one-level path; when unresolved it may just be null data, so
    // it must NOT carry the structural nested-path hint.
    var sig =
        new MethodSignature("ExpenseService", "pay", List.of(), "Paying {expense.payer}", null);
    var node = new TraceNode(sig, List.of(), new TraceOutcome.Returned("\"ok\""));
    var tree = new DefaultTraceTree(List.of(node));

    var output = TemplateWarningCollector.format(TemplateWarningCollector.collect(tree));

    assertThat(output).contains("{expense.payer}");
    assertThat(output).doesNotContain("nested path not supported");
  }

  private static TraceNode chainNode(String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature("Recursive", methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""));
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepTreeIsCollectedWithoutStackOverflow() {
    TraceNode current = chainNode("call1000", List.of());
    for (var i = 0; i < 1_000; i++) {
      current = chainNode("call" + i, List.of(current));
    }
    var tree = new DefaultTraceTree(List.of(current));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).isEmpty();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicTreeIsCollectedWithoutHanging() {
    var childHolder = new ArrayList<TraceNode>();
    var b = chainNode("b", childHolder);
    var a = chainNode("a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var warnings = TemplateWarningCollector.collect(tree);

    assertThat(warnings).isEmpty();
  }
}
