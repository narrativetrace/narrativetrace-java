/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceIdEagerGenerationTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
  }

  @Test
  void traceIdReturnsNonNullBeforeAnyEnterMethod() {
    TraceId traceId = context.traceId();
    assertThat(traceId).isNotNull();
  }

  @Test
  void traceIdReturnsSameValueOnRepeatedCalls() {
    TraceId first = context.traceId();
    TraceId second = context.traceId();
    assertThat(first).isEqualTo(second);
  }

  @Test
  void traceIdMatchesSpanContextTraceIdFromSubsequentEnterMethod() {
    TraceId eagerTraceId = context.traceId();
    var signature =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(new ParameterCapture("id", "\"42\"", false)));
    context.enterMethod(signature);
    context.exitMethodWithReturn("\"done\"");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.spanContext().traceId()).isEqualTo(eagerTraceId);
  }

  @Test
  void resetCausesNewTraceIdGeneration() {
    TraceId first = context.traceId();
    context.reset();
    TraceId second = context.traceId();
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void traceIdIsValidW3cFormat() {
    TraceId traceId = context.traceId();
    assertThat(traceId.toString()).matches("[0-9a-f]{32}");
  }
}
