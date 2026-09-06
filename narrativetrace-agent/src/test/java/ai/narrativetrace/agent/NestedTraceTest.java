/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NestedTraceTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  @Test
  void transformedClassProducesCorrectNestedTrace() throws Exception {
    // Transform both classes
    var calcBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();
    var procBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/OrderProcessor.class")
            .readAllBytes();

    var transformedCalc =
        ClassTransformer.transform(calcBytes, "ai/narrativetrace/agent/sample/Calculator");
    var transformedProc =
        ClassTransformer.transform(procBytes, "ai/narrativetrace/agent/sample/OrderProcessor");

    // Load in correct order (Calculator first since OrderProcessor depends on it)
    var loader = new MultiClassLoader(getClass().getClassLoader());
    loader.addClass("ai.narrativetrace.agent.sample.Calculator", transformedCalc);
    loader.addClass("ai.narrativetrace.agent.sample.OrderProcessor", transformedProc);

    var clazz = loader.loadClass("ai.narrativetrace.agent.sample.OrderProcessor");
    var instance = clazz.getDeclaredConstructor().newInstance();
    var method = clazz.getMethod("processOrder", int.class, int.class);

    var result = method.invoke(instance, 10, 5);
    assertThat(result).isEqualTo(15);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("processOrder");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("add");
    assertThat(((TraceOutcome.Returned) root.children().get(0).outcome()).renderedValue())
        .isEqualTo("15");
  }
}
