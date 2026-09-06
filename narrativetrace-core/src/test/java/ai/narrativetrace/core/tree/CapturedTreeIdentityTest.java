/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A captured tree must carry the trace id its context assigned, even when no node of it kept a span
 * context — the identity a chapter is stamped with comes from the trace, not from a node.
 *
 * <p>Cross-package by design: everything here goes through the public context API.
 */
class CapturedTreeIdentityTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
  }

  @AfterEach
  void tearDown() {
    context.reset();
  }

  @Test
  void capturedTreeCarriesTheTraceIdTheContextAssigned() {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.exitMethodWithReturn("\"order-1\"");

    var tree = context.captureTrace();

    assertThat(tree.traceId()).isNotNull();
    assertThat(tree.traceId()).isEqualTo(context.traceId());
  }

  @Test
  void everyNodeOfACaptureAgreesWithTheTreesTraceId() {
    context.enterMethod(sig("OrderService", "placeOrder"));
    context.enterMethod(sig("PaymentService", "charge"));
    context.exitMethodWithReturn("\"TXN-1\"");
    context.exitMethodWithReturn("\"order-1\"");

    var tree = context.captureTrace();

    var root = tree.roots().get(0);
    assertThat(root.spanContext().traceId()).isEqualTo(tree.traceId());
    assertThat(root.children().get(0).spanContext().traceId()).isEqualTo(tree.traceId());
  }

  @Test
  void aThreadThatTracedNothingIsNotGivenATraceIdByBeingCaptured() {
    // Reading the stack must not generate: asking an idle thread for its trace is not
    // starting a trace on it, and a chapter of an empty tree has no run to identify.
    var tree = context.captureTrace();

    assertThat(tree.isEmpty()).isTrue();
    assertThat(tree.traceId()).isNull();
  }

  @Test
  void anEmptyTreeIsNotATraceAndCarriesNoTraceId() {
    assertThat(new DefaultTraceTree(List.of()).traceId()).isNull();
  }

  @Test
  void aHandBuiltTreeIsItsOwnTraceAndAnswersWithOneStableGeneratedId() {
    // Generated at the tree, once -- so every exporter reading the same tree agrees.
    var tree =
        new DefaultTraceTree(
            List.of(
                new TraceNode(
                    sig("OrderService", "placeOrder"),
                    List.of(),
                    new TraceOutcome.Returned("\"order-1\""))));

    assertThat(tree.traceId()).isNotNull();
    assertThat(tree.traceId().value()).matches("^[0-9a-f]{32}$");
    assertThat(tree.traceId()).isEqualTo(tree.traceId());
  }

  @Test
  void twoHandBuiltTreesOfTheSameShapeAreStillTwoDifferentTraces() {
    var first = new DefaultTraceTree(List.of(handBuiltRoot()));
    var second = new DefaultTraceTree(List.of(handBuiltRoot()));

    assertThat(first.traceId()).isNotEqualTo(second.traceId());
  }

  private static TraceNode handBuiltRoot() {
    return new TraceNode(
        sig("OrderService", "placeOrder"), List.of(), new TraceOutcome.Returned("\"order-1\""));
  }

  private static MethodSignature sig(String className, String methodName) {
    return new MethodSignature(className, methodName, List.of());
  }
}
