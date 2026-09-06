/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AgentRuntimeTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  @Test
  void setAndGetContext() {
    assertThat(AgentRuntime.getContext()).isSameAs(context);
  }

  @Test
  void enterMethodWithoutParameters() {
    AgentRuntime.enterMethod("MyClass", "myMethod");
    AgentRuntime.exitMethodWithReturn("result");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().className()).isEqualTo("MyClass");
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("myMethod");
    assertThat(tree.roots().get(0).signature().parameters()).isEmpty();
  }

  @Test
  void splitsQualifiedNameIntoSimpleClassNameAndPackage() {
    AgentRuntime.enterMethod("com.acme.billing.OrderService", "placeOrder");
    AgentRuntime.exitMethodWithReturn("result");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.className()).isEqualTo("OrderService");
    assertThat(signature.packageName()).isEqualTo("com.acme.billing");
  }

  @Test
  void carriesRawNarratedTemplateOnSignature() {
    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[] {"customerId"},
        new Object[] {"C-123"},
        new boolean[] {false},
        "Placing order for {customerId}");
    AgentRuntime.exitMethodWithReturn("ok");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.narration()).isEqualTo("Placing order for C-123");
    assertThat(signature.narrationTemplate()).isEqualTo("Placing order for {customerId}");
  }

  @Test
  void enterMethodWithDescriptorCapturesParameterAndReturnTypes() {
    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "find",
        new String[] {"id", "limit", "window"},
        new Object[] {"X", 3, new long[] {1L}},
        new boolean[] {false, false, false},
        null,
        "(Ljava/lang/String;I[J)Ljava/lang/String;");
    AgentRuntime.exitMethodWithReturn("found");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.returnType()).isEqualTo("java.lang.String");
    assertThat(signature.parameters().get(0).type()).isEqualTo("java.lang.String");
    assertThat(signature.parameters().get(1).type()).isEqualTo("int");
    assertThat(signature.parameters().get(2).type()).isEqualTo("long[]");
  }

  @Test
  void enterMethodWithNullDescriptorLeavesTypesNull() {
    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[] {"customerId"},
        new Object[] {"C-123"},
        new boolean[] {false},
        null);
    AgentRuntime.exitMethodWithReturn("ok");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.returnType()).isNull();
    assertThat(signature.parameters().get(0).type()).isNull();
  }

  @Test
  void bareEnterMethodWithDescriptorCapturesReturnType() {
    AgentRuntime.enterMethod("com.acme.OrderService", "reset", "()V");
    AgentRuntime.exitMethodWithReturn(null);

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.returnType()).isEqualTo("void");
    assertThat(signature.parameters()).isEmpty();
  }

  @Test
  void enterMethodCapturesInstanceIdWhenEnabled() {
    var config = new NarrativeTraceConfig();
    config.setCaptureInstanceIds(true);
    var idContext = new ThreadLocalNarrativeContext(config);
    idContext.reset();
    AgentRuntime.setContext(idContext);
    var receiver = new Object();

    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[0],
        new Object[0],
        new boolean[0],
        null,
        "()V",
        receiver);
    AgentRuntime.exitMethodWithReturn(null);

    var signature = idContext.captureTrace().roots().get(0).signature();
    assertThat(signature.instanceId())
        .isEqualTo(Integer.toHexString(System.identityHashCode(receiver)));
  }

  @Test
  void instanceIdStaysNullByDefaultAndForStaticMethods() {
    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[0],
        new Object[0],
        new boolean[0],
        null,
        "()V",
        new Object());
    AgentRuntime.exitMethodWithReturn(null);

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.instanceId()).isNull();
  }

  @Test
  void enterMethodCapturesBakedSourceLocationWhenEnabled() {
    var config = new NarrativeTraceConfig();
    config.setCaptureSourceLocation(true);
    var srcContext = new ThreadLocalNarrativeContext(config);
    srcContext.reset();
    AgentRuntime.setContext(srcContext);

    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[0],
        new Object[0],
        new boolean[0],
        null,
        "()V",
        null,
        "OrderService.java",
        42);
    AgentRuntime.exitMethodWithReturn(null);

    var source = srcContext.captureTrace().roots().get(0).signature().source();
    assertThat(source).isNotNull();
    assertThat(source.file()).isEqualTo("OrderService.java");
    assertThat(source.line()).isEqualTo(42);
  }

  @Test
  void sourceLocationStaysNullWhenEnabledButUnknown() {
    var config = new NarrativeTraceConfig();
    config.setCaptureSourceLocation(true);
    var srcContext = new ThreadLocalNarrativeContext(config);
    srcContext.reset();
    AgentRuntime.setContext(srcContext);

    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[0],
        new Object[0],
        new boolean[0],
        null,
        "()V",
        null,
        null,
        -1);
    AgentRuntime.exitMethodWithReturn(null);

    assertThat(srcContext.captureTrace().roots().get(0).signature().source()).isNull();
  }

  @Test
  void sourceLocationStaysNullByDefaultAndWithoutLineInfo() {
    AgentRuntime.enterMethod(
        "com.acme.OrderService",
        "placeOrder",
        new String[0],
        new Object[0],
        new boolean[0],
        null,
        "()V",
        null,
        "OrderService.java",
        42);
    AgentRuntime.exitMethodWithReturn(null);

    assertThat(context.captureTrace().roots().get(0).signature().source()).isNull();
  }

  @Test
  void defaultPackageClassKeepsNullPackage() {
    AgentRuntime.enterMethod("MyClass", "myMethod");
    AgentRuntime.exitMethodWithReturn("result");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.className()).isEqualTo("MyClass");
    assertThat(signature.packageName()).isNull();
  }

  @Test
  void skipsEnterMethodCaptureWhenContextIsNotActive() {
    var offConfig = new NarrativeTraceConfig(TracingLevel.OFF);
    var offContext = new ThreadLocalNarrativeContext(offConfig);
    offContext.reset();
    AgentRuntime.setContext(offContext);

    AgentRuntime.enterMethod(
        "MyClass",
        "myMethod",
        new String[] {"x"},
        new Object[] {"val"},
        new boolean[] {false},
        null);
    AgentRuntime.exitMethodWithReturn("result");

    assertThat(offContext.captureTrace().roots()).isEmpty();
  }

  @Test
  void skipsExitMethodWithReturnRenderingWhenContextIsNotActive() {
    var offConfig = new NarrativeTraceConfig(TracingLevel.OFF);
    var offContext = new ThreadLocalNarrativeContext(offConfig);
    offContext.reset();
    AgentRuntime.setContext(offContext);

    AgentRuntime.exitMethodWithReturn(new Object());

    assertThat(offContext.captureTrace().roots()).isEmpty();
  }

  @Test
  void resolveErrorContextReturnsNullForNonMatchingException() {
    var result =
        AgentRuntime.resolveErrorContext(
            new RuntimeException("boom"),
            new String[] {"Error for {x}"},
            new String[] {"Ljava/lang/IllegalArgumentException;"},
            new String[] {"x"},
            new Object[] {"val"},
            new boolean[] {false});

    assertThat(result).isNull();
  }

  @Test
  void resolveErrorContextReturnsNullForInvalidDescriptor() {
    var result =
        AgentRuntime.resolveErrorContext(
            new RuntimeException("boom"),
            new String[] {"Error"},
            new String[] {"InvalidDescriptor"},
            new String[] {"x"},
            new Object[] {"val"},
            new boolean[] {false});

    assertThat(result).isNull();
  }

  @Test
  void initializeProducesSlf4jLogsWhenOnClasspath() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("myapp.traces");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    AgentRuntime.initialize(
        new AgentConfig(
            List.of(), "myapp.traces", ai.narrativetrace.api.config.TracingLevel.DETAIL));
    AgentRuntime.getContext().enterMethod(new MethodSignature("Svc", "run", List.of()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("Svc.run");
  }

  @Test
  void initializeRoutesToConfiguredLoggerName() {
    var logbackLogger = (Logger) LoggerFactory.getLogger("custom.logger");
    logbackLogger.setLevel(Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);

    AgentRuntime.initialize(
        new AgentConfig(
            List.of(), "custom.logger", ai.narrativetrace.api.config.TracingLevel.DETAIL));
    AgentRuntime.getContext().enterMethod(new MethodSignature("Svc", "handle", List.of()));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("Svc.handle");
  }

  static final class RenderProbe {
    int renderCount;

    @Override
    public String toString() {
      renderCount++;
      return "probe";
    }
  }

  @Test
  void doesNotRenderParameterValuesBelowDetailLevel() {
    var narrativeContext =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE));
    narrativeContext.reset();
    AgentRuntime.setContext(narrativeContext);
    var probe = new RenderProbe();

    AgentRuntime.enterMethod(
        "C", "m", new String[] {"p"}, new Object[] {probe}, new boolean[] {false}, null);
    AgentRuntime.exitMethodWithReturn("\"ok\"");

    assertThat(probe.renderCount).isZero();
    var root = narrativeContext.captureTrace().roots().get(0);
    assertThat(root.signature().parameters().get(0).renderedValue()).isEmpty();
  }

  @Test
  void initializeHonorsConfiguredLevelSoOffCapturesNothing() {
    AgentRuntime.initialize(
        new AgentConfig(
            List.of(), "narrativetrace", ai.narrativetrace.api.config.TracingLevel.OFF));

    assertThat(AgentRuntime.getContext().isActive()).isFalse();
  }

  @Test
  void enterMethodReturnsPrevScopeAndSetsParent() {
    String prevScope = AgentRuntime.enterMethod("Outer", "run");
    // Nested call should see Outer as parent via scoped parent
    AgentRuntime.enterMethod("Inner", "exec");
    AgentRuntime.exitMethodWithReturn("\"inner\"");
    AgentRuntime.endScope(prevScope);
    AgentRuntime.exitMethodWithReturn("\"outer\"");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var outer = tree.roots().get(0);
    assertThat(outer.signature().className()).isEqualTo("Outer");
    assertThat(outer.children()).hasSize(1);
    assertThat(outer.children().get(0).signature().className()).isEqualTo("Inner");
  }

  @Test
  void endScopeRestoresPreviousScope() {
    String prev0 = AgentRuntime.enterMethod("A", "a");
    String prev1 = AgentRuntime.enterMethod("B", "b");
    AgentRuntime.endScope(prev1); // restore to A's scope
    AgentRuntime.enterMethod("C", "c");
    AgentRuntime.exitMethodWithReturn("\"c\"");
    AgentRuntime.endScope(prev0);
    AgentRuntime.exitMethodWithReturn("\"b\"");
    AgentRuntime.exitMethodWithReturn("\"a\"");

    var tree = context.captureTrace();
    var aNode = tree.roots().get(0);
    // C should be child of A (scope restored), not B
    var childNames = aNode.children().stream().map(n -> n.signature().className()).toList();
    assertThat(childNames).contains("C");
  }

  @Test
  void resolveErrorContextReturnsNullForNonexistentExceptionClass() {
    var result =
        AgentRuntime.resolveErrorContext(
            new RuntimeException("boom"),
            new String[] {"Error for {x}"},
            new String[] {"Lcom/nonexistent/FakeException;"},
            new String[] {"x"},
            new Object[] {"val"},
            new boolean[] {false});

    assertThat(result).isNull();
  }
}
