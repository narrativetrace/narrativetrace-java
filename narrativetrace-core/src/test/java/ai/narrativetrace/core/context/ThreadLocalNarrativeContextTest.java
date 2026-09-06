/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ClientIp;
import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.ServiceIdentity;
import ai.narrativetrace.api.event.SessionId;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TenantId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.core.pipeline.EventPipeline;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ThreadLocalNarrativeContextTest {

  @Test
  void customPipelineIsUsedAsGivenNeverWrapped() {
    var custom = new FakeCustomPipeline();
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), custom);

    var events = context.events();

    assertThat(custom.flushed)
        .as("the context must query the supplied pipeline itself, never a silent replacement")
        .isTrue();
    assertThat(events).isEmpty();
  }

  /** Custom pipeline retaining nothing: proves the context takes the pipeline as given. */
  private static final class FakeCustomPipeline
      implements ai.narrativetrace.core.pipeline.EventPipeline {
    private boolean flushed;

    @Override
    public void publish(TraceEvent event) {}

    @Override
    public void flush() {
      flushed = true;
    }

    @Override
    public void close() {}
  }

  @Test
  void enterExitSingleMethodProducesOneRootNode() {
    var context = new ThreadLocalNarrativeContext();
    var signature =
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)));

    context.enterMethod(signature);
    context.exitMethodWithReturn("\"order-42\"");

    var tree = context.captureTrace();
    assertThat(tree.isEmpty()).isFalse();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.children()).isEmpty();
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Returned.class);
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("\"order-42\"");
  }

  @Test
  void nestedEnterExitProducesChildNodes() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    context.exitMethodWithReturn("true");
    context.exitMethodWithReturn("\"order-42\"");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.children()).hasSize(1);

    var child = root.children().get(0);
    assertThat(child.signature().className()).isEqualTo("InventoryService");
    assertThat(child.children()).isEmpty();
    assertThat(((TraceOutcome.Returned) child.outcome()).renderedValue()).isEqualTo("true");
  }

  @Test
  void exitWithExceptionCapturesThrew() {
    var context = new ThreadLocalNarrativeContext();
    var exception = new RuntimeException("insufficient funds");

    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(exception, null);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(((TraceOutcome.Threw) root.outcome()).exception()).isSameAs(exception);
  }

  @Test
  void capturesDurationOnMethodExit() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    // Simulate some work
    busyWait(5_000_000L); // at least 5ms
    context.exitMethodWithReturn("order-42");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.durationNanos()).isGreaterThan(0L);
  }

  @Test
  void nestedCallsHaveIndependentDurations() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    busyWait(1_000_000L);
    context.enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    busyWait(2_000_000L);
    context.exitMethodWithReturn("true");
    busyWait(1_000_000L);
    context.exitMethodWithReturn("\"order-42\"");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    var child = root.children().get(0);

    assertThat(child.durationNanos()).isGreaterThan(0L);
    assertThat(root.durationNanos()).isGreaterThan(child.durationNanos());
  }

  @Test
  void exceptionExitCapturesDuration() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    busyWait(2_000_000L);
    context.exitMethodWithException(new RuntimeException("declined"), null);

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(root.durationNanos()).isGreaterThan(0L);
  }

  /**
   * The shape a user meets: a pipeline closed at shutdown, one more traced call on the way out, and
   * a capture that flushes it. The flush drained the event into a closed {@code
   * SubmissionPublisher}, and the {@link IllegalStateException} that answers had no boundary
   * between it and the caller.
   */
  @Test
  void aCaptureAfterThePipelineClosedIsNotTheCallersException() {
    var pipeline = new DualPathPipeline(null, new BufferedEventConsumer(8, false));
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    pipeline.close();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("\"order-42\"");

    assertThat(context.captureTrace().roots()).hasSize(1);
    context.reset();
  }

  @Test
  void resetClearsTrace() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("order-42");
    assertThat(context.captureTrace().isEmpty()).isFalse();

    context.reset();

    assertThat(context.captureTrace().isEmpty()).isTrue();
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void detailLevelCapturesEverythingIncludingParameterValues() {
    var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(
                new ParameterCapture("customerId", "\"C-123\"", false),
                new ParameterCapture("quantity", "5", false))));
    context.exitMethodWithReturn("\"order-42\"");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    var params = root.signature().parameters();
    assertThat(params).hasSize(2);
    assertThat(params.get(0).renderedValue()).isEqualTo("\"C-123\"");
    assertThat(params.get(1).renderedValue()).isEqualTo("5");

    var outcome = (TraceOutcome.Returned) root.outcome();
    assertThat(outcome.renderedValue()).isEqualTo("\"order-42\"");
  }

  @Test
  void defaultConstructorUsesDetailLevel() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false))));
    context.exitMethodWithReturn("order-42");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().parameters().get(0).renderedValue())
        .isEqualTo("\"C-123\"");
  }

  @Test
  void exitEventCarriesTheEnteringSignature() {
    var context = new ThreadLocalNarrativeContext();
    var signature =
        new MethodSignature(
            "OrderService", "placeOrder", List.of(), null, null, null, "com.acme.billing");

    context.enterMethod(signature);
    context.exitMethodWithReturn("\"order-42\"");

    var exit =
        context.events().stream()
            .filter(TraceEvent.ExitEvent.class::isInstance)
            .map(TraceEvent.ExitEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(exit.signature()).isNotNull();
    assertThat(exit.signature().className()).isEqualTo("OrderService");
    assertThat(exit.signature().packageName()).isEqualTo("com.acme.billing");
  }

  @Test
  void spansCarryDetectedResourceIdentityByDefault() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn(null);

    var sc = firstEnter(context).spanContext();
    assertThat(sc.processPid()).isEqualTo(ProcessHandle.current().pid());
    assertThat(sc.runtimeVersion()).isEqualTo(Runtime.version().toString());
  }

  @Test
  void resourceCaptureOptOutLeavesResourceFieldsNull() {
    var config = new ai.narrativetrace.core.config.NarrativeTraceConfig();
    config.setCaptureResource(false);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn(null);

    var sc = firstEnter(context).spanContext();
    assertThat(sc.hostName()).isNull();
    assertThat(sc.processPid()).isNull();
    assertThat(sc.runtimeVersion()).isNull();
  }

  private static TraceEvent.EnterEvent firstEnter(ThreadLocalNarrativeContext context) {
    return context.events().stream()
        .filter(TraceEvent.EnterEvent.class::isInstance)
        .map(TraceEvent.EnterEvent.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void spansOfOneTraceShareOneWallClockAnchor() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithReturn(null);
    context.exitMethodWithReturn(null);

    var enters =
        context.events().stream()
            .filter(TraceEvent.EnterEvent.class::isInstance)
            .map(TraceEvent.EnterEvent.class::cast)
            .toList();
    assertThat(enters).hasSize(2);
    assertThat(enters.get(0).spanContext().traceAnchor()).isNotNull();
    assertThat(enters.get(0).spanContext().traceAnchor())
        .isSameAs(enters.get(1).spanContext().traceAnchor());
  }

  @Test
  void everyEnterEventCarriesTheExecutingThreadIdentity() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("\"order-42\"");

    var enter =
        context.events().stream()
            .filter(TraceEvent.EnterEvent.class::isInstance)
            .map(TraceEvent.EnterEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(enter.thread()).isNotNull();
    assertThat(enter.thread().threadName()).isEqualTo(Thread.currentThread().getName());
    assertThat(enter.thread().threadId()).isEqualTo(Thread.currentThread().getId());
    assertThat(enter.thread().virtual()).isFalse();
  }

  @Test
  void deferredAndExceptionExitsCarryTheEnteringSignature() {
    var context = new ThreadLocalNarrativeContext();
    var deferred = new MethodSignature("AsyncService", "fetch", List.of());
    SpanId spanId = context.enterMethod(deferred);
    context.detachFrame(spanId);
    context.exitMethodWithReturn("\"later\"", spanId);

    context.enterMethod(new MethodSignature("FailingService", "boom", List.of()));
    context.exitMethodWithException(new IllegalStateException("kaput"), null);

    var exits =
        context.events().stream()
            .filter(TraceEvent.ExitEvent.class::isInstance)
            .map(TraceEvent.ExitEvent.class::cast)
            .toList();
    assertThat(exits).hasSize(2);
    assertThat(exits.get(0).signature().className()).isEqualTo("AsyncService");
    assertThat(exits.get(1).signature().className()).isEqualTo("FailingService");
  }

  @Test
  void parameterSuppressionPreservesIdentityAndTemplateFields() {
    var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(new ParameterCapture("customerId", "\"C-123\"", false)),
            "Placing order for C-123",
            null,
            "Placing order for {customerId}",
            "com.acme.billing"));
    context.exitMethodWithReturn("order-42");

    var signature = context.captureTrace().roots().get(0).signature();
    assertThat(signature.parameters().get(0).renderedValue()).isEmpty();
    assertThat(signature.packageName()).isEqualTo("com.acme.billing");
    assertThat(signature.narrationTemplate()).isEqualTo("Placing order for {customerId}");
    assertThat(signature.narration()).isEqualTo("Placing order for C-123");
  }

  @Test
  void narrativeLevelCapturesAllMethodsButSuppressesParameterValues() {
    var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(
        new MethodSignature(
            "OrderService",
            "placeOrder",
            List.of(
                new ParameterCapture("customerId", "\"C-123\"", false),
                new ParameterCapture("quantity", "5", false))));
    context.exitMethodWithReturn("order-42");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    // Parameters are captured but values are suppressed
    var params = root.signature().parameters();
    assertThat(params).hasSize(2);
    assertThat(params.get(0).name()).isEqualTo("customerId");
    assertThat(params.get(0).renderedValue()).isEmpty();
    assertThat(params.get(1).name()).isEqualTo("quantity");
    assertThat(params.get(1).renderedValue()).isEmpty();
  }

  @Test
  void summaryLevelCapturesSingleCallAsRootAndLeaf() {
    var config = new NarrativeTraceConfig(TracingLevel.SUMMARY);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("order-42");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  void summaryLevelCapturesRootAndLeafDiscardsIntermediate() {
    var config = new NarrativeTraceConfig(TracingLevel.SUMMARY);
    var context = new ThreadLocalNarrativeContext(config);

    // root → intermediate → leaf
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("ValidationService", "validate", List.of()));
    context.enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    context.exitMethodWithReturn("true"); // leaf — captured
    context.exitMethodWithReturn("valid"); // intermediate — discarded
    context.exitMethodWithReturn("order-42"); // root — captured

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);

    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    // intermediate was discarded, so root has one child (the leaf)
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().className()).isEqualTo("InventoryService");
  }

  @Test
  void summaryLevelCapturesExceptions() {
    var config = new NarrativeTraceConfig(TracingLevel.SUMMARY);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new RuntimeException("declined"), null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelCapturesOnlyExceptionPaths() {
    var config = new NarrativeTraceConfig(TracingLevel.ERRORS);
    var context = new ThreadLocalNarrativeContext(config);

    // Normal call — should not be captured
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("order-42");

    assertThat(context.captureTrace().isEmpty()).isTrue();

    // Exception call — should be captured
    context.reset();
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new RuntimeException("declined"), null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelPreservesCaughtChildExceptionWhenParentReturns() {
    var config = new NarrativeTraceConfig(TracingLevel.ERRORS);
    var context = new ThreadLocalNarrativeContext(config);

    // Parent calls child; child throws; parent catches and returns normally
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new RuntimeException("declined"), null);
    context.exitMethodWithReturn("fallback-order");

    // Parent path preserved — shows how the error was reached
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().className()).isEqualTo("PaymentService");
    assertThat(root.children().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelDiscardsSuccessfulSiblingKeepsFailedSibling() {
    var config = new NarrativeTraceConfig(TracingLevel.ERRORS);
    var context = new ThreadLocalNarrativeContext(config);

    // Parent calls two children; first succeeds, second throws; parent catches
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("InventoryService", "checkStock", List.of()));
    context.exitMethodWithReturn("true"); // succeeds — pruned
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new RuntimeException("declined"), null); // throws — kept
    context.exitMethodWithReturn("fallback"); // parent catches

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().className()).isEqualTo("OrderService");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().className()).isEqualTo("PaymentService");
  }

  @Test
  void errorsLevelPromotesThroughMultipleCatchLevels() {
    var config = new NarrativeTraceConfig(TracingLevel.ERRORS);
    var context = new ThreadLocalNarrativeContext(config);

    // grandparent → parent → child; child throws, parent catches, grandparent returns
    context.enterMethod(new MethodSignature("Controller", "handleRequest", List.of()));
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(new RuntimeException("declined"), null);
    context.exitMethodWithReturn("fallback"); // parent catches
    context.exitMethodWithReturn("ok"); // grandparent returns

    // Full path preserved: Controller → OrderService → PaymentService
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var controller = tree.roots().get(0);
    assertThat(controller.signature().className()).isEqualTo("Controller");
    assertThat(controller.children()).hasSize(1);
    var order = controller.children().get(0);
    assertThat(order.signature().className()).isEqualTo("OrderService");
    assertThat(order.children()).hasSize(1);
    assertThat(order.children().get(0).signature().className()).isEqualTo("PaymentService");
    assertThat(order.children().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void isActiveReturnsTrueAtDetailLevel() {
    var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    var context = new ThreadLocalNarrativeContext(config);
    assertThat(context.isActive()).isTrue();
  }

  @Test
  void isActiveReturnsFalseAtOffLevel() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);
    assertThat(context.isActive()).isFalse();
  }

  @Test
  void capturesParameterValuesOnlyAtDetailLevel() {
    assertThat(
            new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL))
                .capturesParameterValues())
        .isTrue();
    assertThat(
            new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE))
                .capturesParameterValues())
        .isFalse();
    assertThat(
            new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF))
                .capturesParameterValues())
        .isFalse();
  }

  @Test
  void skipsCaptureEntirelyWhenLevelIsOff() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("order-42");

    var tree = context.captureTrace();
    assertThat(tree.isEmpty()).isTrue();
  }

  @Test
  void offLevelSkipsExceptionCapture() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.exitMethodWithException(new RuntimeException("fail"), null);

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void levelChangeFromOffToDetailDoesNotThrowOnExit() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    config.setLevel(TracingLevel.DETAIL);
    context.exitMethodWithReturn("ok");

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void levelChangeFromOffToDetailDoesNotThrowOnExceptionExit() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    config.setLevel(TracingLevel.DETAIL);
    context.exitMethodWithException(new RuntimeException("fail"), null);

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void levelChangeFromDetailToOffDoesNotThrow() {
    var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    var context = new ThreadLocalNarrativeContext(config);

    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    config.setLevel(TracingLevel.OFF);
    context.exitMethodWithReturn("ok");

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void downgradeToOffDuringInFlightMethodDoesNotLeakFrame() {
    var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    var context = new ThreadLocalNarrativeContext(config);

    // Start a method while DETAIL is active — pushes a frame
    context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    // Downgrade to OFF mid-flight
    config.setLevel(TracingLevel.OFF);
    context.exitMethodWithReturn("ok");

    // Restore DETAIL and trace a second method
    config.setLevel(TracingLevel.DETAIL);
    context.enterMethod(new MethodSignature("Svc", "second", List.of()));
    context.exitMethodWithReturn("ok");

    var tree = context.captureTrace();
    // second call should be its own root, not a child of a leaked frame
    assertThat(tree.roots()).anyMatch(r -> r.signature().methodName().equals("second"));
    var secondRoots =
        tree.roots().stream().filter(r -> r.signature().methodName().equals("second")).toList();
    assertThat(secondRoots).hasSize(1);
    assertThat(secondRoots.get(0).children()).isEmpty();
  }

  @Test
  void downgradeToOffDuringInFlightExceptionDoesNotLeakFrame() {
    var config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    var context = new ThreadLocalNarrativeContext(config);

    // Start a method while DETAIL is active — pushes a frame
    context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    // Downgrade to OFF mid-flight
    config.setLevel(TracingLevel.OFF);
    context.exitMethodWithException(new RuntimeException("fail"), null);

    // Restore DETAIL and trace a second method
    config.setLevel(TracingLevel.DETAIL);
    context.enterMethod(new MethodSignature("Svc", "second", List.of()));
    context.exitMethodWithReturn("ok");

    var tree = context.captureTrace();
    var secondRoots =
        tree.roots().stream().filter(r -> r.signature().methodName().equals("second")).toList();
    assertThat(secondRoots).hasSize(1);
    assertThat(secondRoots.get(0).children()).isEmpty();
  }

  @Test
  void errorContextFlowsToTraceNodeSignatureOnException() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("PaymentService", "charge", List.of()));
    context.exitMethodWithException(
        new RuntimeException("declined"), "Payment was declined for customer C-123");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().errorContext())
        .isEqualTo("Payment was declined for customer C-123");
  }

  @Test
  void childThreadGetsFreshContextViaSnapshot() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    // Trace on parent thread
    context.enterMethod(new MethodSignature("ParentService", "parentMethod", List.of()));
    context.exitMethodWithReturn("parent-result");

    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(
                          new MethodSignature("ChildService", "childMethod", List.of()));
                      context.exitMethodWithReturn("child-result");
                      return context.captureTrace();
                    }
                  })
              .get();

      // Child thread captures its own trace independently
      assertThat(childTree.roots()).hasSize(1);
      assertThat(childTree.roots().get(0).signature().className()).isEqualTo("ChildService");

      // The child ran under the parent's snapshot after parentMethod returned, so the parent's
      // trace reports both stories — one root each, in the order they happened.
      var parentTree = context.captureTrace();
      assertThat(parentTree.roots()).hasSize(2);
      assertThat(parentTree.roots().get(0).signature().className()).isEqualTo("ParentService");
      assertThat(parentTree.roots().get(1).signature().className()).isEqualTo("ChildService");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void scopeRestoresPreviousStateOnPooledThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // Pre-populate the pooled thread with existing traces
      executor
          .submit(
              () -> {
                context.enterMethod(
                    new MethodSignature("ExistingService", "existingMethod", List.of()));
                context.exitMethodWithReturn("existing");
              })
          .get();

      // Now activate snapshot on the same pooled thread — should get fresh stack
      executor
          .submit(
              () -> {
                try (var scope = snapshot.activate()) {
                  context.enterMethod(new MethodSignature("NewService", "newMethod", List.of()));
                  context.exitMethodWithReturn("new");
                  var duringScope = context.captureTrace();
                  assertThat(duringScope.roots()).hasSize(1);
                  assertThat(duringScope.roots().get(0).signature().className())
                      .isEqualTo("NewService");
                }

                // After scope closes, the previous state should be restored
                var afterScope = context.captureTrace();
                assertThat(afterScope.roots()).hasSize(1);
                assertThat(afterScope.roots().get(0).signature().className())
                    .isEqualTo("ExistingService");
              })
          .get();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void wrapRunnableActivatesScopeAndCloses() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var wrappedTree =
          executor
              .submit(
                  () -> {
                    var wrapped =
                        snapshot.wrap(
                            (Runnable)
                                () -> {
                                  context.enterMethod(
                                      new MethodSignature("WrappedService", "run", List.of()));
                                  context.exitMethodWithReturn(null);
                                });
                    wrapped.run();
                    return context.captureTrace();
                  })
              .get();

      // After the wrapped runnable completes, scope is closed — child thread has empty tree
      assertThat(wrappedTree.isEmpty()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void wrapCallableActivatesScopeAndReturnsResult() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Callable<ai.narrativetrace.api.tree.TraceTree> callable =
          () -> {
            context.enterMethod(new MethodSignature("CalcService", "compute", List.of()));
            context.exitMethodWithReturn("42");
            return context.captureTrace();
          };
      var result = executor.submit(snapshot.wrap(callable)).get();

      assertThat(result.roots()).hasSize(1);
      assertThat(result.roots().get(0).signature().className()).isEqualTo("CalcService");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void wrapSupplierWorksWithCompletableFuture() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Supplier<ai.narrativetrace.api.tree.TraceTree> supplier =
          () -> {
            context.enterMethod(new MethodSignature("AsyncService", "fetch", List.of()));
            context.exitMethodWithReturn("data");
            return context.captureTrace();
          };
      var result = CompletableFuture.supplyAsync(snapshot.wrap(supplier), executor).get();

      assertThat(result.roots()).hasSize(1);
      assertThat(result.roots().get(0).signature().className()).isEqualTo("AsyncService");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void snapshotReturnsNonNullAndActivateReturnsScope() {
    var context = new ThreadLocalNarrativeContext();

    var snapshot = context.snapshot();
    assertThat(snapshot).isNotNull();

    var scope = snapshot.activate();
    assertThat(scope).isNotNull();
    scope.close(); // should not throw
  }

  @Test
  void capturedNodeHasNonZeroStartTimeNanos() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.exitMethodWithReturn("ok");

    var root = context.captureTrace().roots().get(0);
    assertThat(root.startTimeNanos()).isGreaterThan(0L);
  }

  @Test
  void siblingNodesHaveOrderedStartTimeNanos() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("Parent", "run", List.of()));
    context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    context.exitMethodWithReturn("a");
    busyWait(1_000_000L);
    context.enterMethod(new MethodSignature("Svc", "second", List.of()));
    context.exitMethodWithReturn("b");
    context.exitMethodWithReturn("done");

    var parent = context.captureTrace().roots().get(0);
    var first = parent.children().get(0);
    var second = parent.children().get(1);
    assertThat(second.startTimeNanos()).isGreaterThanOrEqualTo(first.startTimeNanos());
  }

  @Test
  void nestedChildStartTimeIsAfterParentStartTime() {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("Parent", "run", List.of()));
    busyWait(1_000_000L);
    context.enterMethod(new MethodSignature("Child", "work", List.of()));
    context.exitMethodWithReturn("done");
    context.exitMethodWithReturn("ok");

    var parent = context.captureTrace().roots().get(0);
    var child = parent.children().get(0);
    assertThat(child.startTimeNanos()).isGreaterThan(parent.startTimeNanos());
  }

  @Test
  void handleBasedExitCompletesCorrectFrame() {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "outer", List.of()));
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "inner", List.of()));
    context.exitMethodWithReturn("inner-val", h2);
    context.exitMethodWithReturn("outer-val", h1);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("outer");
    assertThat(((TraceOutcome.Returned) root.outcome()).renderedValue()).isEqualTo("outer-val");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("inner");
  }

  @Test
  void handleBasedExitWithNullUsesLifo() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "outer", List.of()));
    context.enterMethod(new MethodSignature("Svc", "inner", List.of()));

    context.exitMethodWithReturn("inner-val", null);
    context.exitMethodWithReturn("outer-val", null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("outer");
    assertThat(tree.roots().get(0).children().get(0).signature().methodName()).isEqualTo("inner");
  }

  @Test
  void handleBasedExceptionExitCompletesCorrectFrame() {
    var context = new ThreadLocalNarrativeContext();
    var exception = new RuntimeException("boom");

    SpanId handle = context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.exitMethodWithException(exception, "error context", handle);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(tree.roots().get(0).signature().errorContext()).isEqualTo("error context");
  }

  @Test
  void handleBasedExitPopsSpecificFrameNotLifoTop() {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "second", List.of()));

    // Exit first (bottom of stack) by handle — not LIFO order
    context.exitMethodWithReturn("first-val", h1);
    context.exitMethodWithReturn("second-val", h2);

    // h2 entered while h1 was active → h2 is a child of h1 in the event trail
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("first");
    assertThat(((TraceOutcome.Returned) tree.roots().get(0).outcome()).renderedValue())
        .isEqualTo("first-val");
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature().methodName()).isEqualTo("second");
  }

  @Test
  void detachFrameRemovesFromActiveStack() {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    context.detachFrame(h1);

    // New enter should not nest under the detached frame
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "second", List.of()));
    context.exitMethodWithReturn("second-val", h2);

    // Complete the detached frame
    context.exitMethodWithReturn("first-val", h1);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);
    // Both should be roots — second is NOT a child of first (entry order)
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("first");
    assertThat(tree.roots().get(0).children()).isEmpty();
    assertThat(tree.roots().get(1).signature().methodName()).isEqualTo("second");
  }

  @Test
  void detachedFramePreservesChildrenAccumulatedBeforeDetach() {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "parent", List.of()));
    // Add a child before detaching
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "child", List.of()));
    context.exitMethodWithReturn("child-val", h2);
    // Now detach parent
    context.detachFrame(h1);
    // Complete detached parent later
    context.exitMethodWithReturn("parent-val", h1);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("parent");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("child");
  }

  @Test
  void detachedFrameCompletedFromDifferentThreadAttachesToOriginStack() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    // Enter and detach on main thread
    SpanId handle = context.enterMethod(new MethodSignature("Svc", "asyncMethod", List.of()));
    context.detachFrame(handle);

    // Complete from a different thread
    var executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(() -> context.exitMethodWithReturn("\"resolved\"", handle)).get();
    } finally {
      executor.shutdown();
    }

    // The node should appear in the main thread's trace
    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("asyncMethod");
    assertThat(((TraceOutcome.Returned) tree.roots().get(0).outcome()).renderedValue())
        .isEqualTo("\"resolved\"");
  }

  @Test
  void twoConcurrentDetachedCompletionsProduceSiblingRoots() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "fast", List.of()));
    context.detachFrame(h1);
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "slow", List.of()));
    context.detachFrame(h2);

    var executor = Executors.newFixedThreadPool(2);
    try {
      var f1 =
          CompletableFuture.runAsync(
              () -> context.exitMethodWithReturn("\"fast-result\"", h1), executor);
      var f2 =
          CompletableFuture.runAsync(
              () -> context.exitMethodWithReturn("\"slow-result\"", h2), executor);
      CompletableFuture.allOf(f1, f2).join();
    } finally {
      executor.shutdown();
    }

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);
    var names = tree.roots().stream().map(r -> r.signature().methodName()).sorted().toList();
    assertThat(names).containsExactly("fast", "slow");
  }

  @Test
  void detachedFrameWithGcedOriginStackIsSilentlyDiscarded() {
    var context = new ThreadLocalNarrativeContext();

    SpanId handle = context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.detachFrame(handle);

    // Simulate origin stack being GC'd by resetting (removes strong ref from ThreadLocal)
    context.reset();
    System.gc(); // NOPMD - intentional GC for WeakReference test

    // Completing the detached frame should not throw
    context.exitMethodWithReturn("\"val\"", handle);

    // Nothing should appear in the (new) trace
    var tree = context.captureTrace();
    assertThat(tree.roots()).isEmpty();
  }

  @Test
  void resetClearsStaleDetachedFrames() {
    var context = new ThreadLocalNarrativeContext();

    SpanId handle = context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    context.detachFrame(handle);
    // Reset clears the ThreadLocal stack and cleans stale detached frames
    context.reset();
    System.gc(); // NOPMD - intentional GC for WeakReference test

    // Completing after reset should not throw or produce a node
    context.exitMethodWithReturn("\"val\"", handle);
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  /**
   * Owner ruling 3 of 2026-08-31, pinned: capture at {@code OFF} returns the empty tree without
   * building one, but the drain happens first and unconditionally. In the default pipeline capture
   * is the only thing that empties the ring, so a capture that skipped the flush would strand every
   * event a level change had already published.
   */
  @Test
  void captureTraceAtOffStillDrainsThePipeline() {
    var pipeline = new FlushCountingPipeline();
    var context =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF), pipeline);

    var tree = context.captureTrace();

    assertThat(pipeline.flushes).isEqualTo(1);
    assertThat(tree.isEmpty()).isTrue();
  }

  /** The short-circuit must not invent a trace id for a thread that never traced. */
  @Test
  void captureTraceAtOffReportsNoTraceIdForAThreadThatNeverTraced() {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF));

    assertThat(context.captureTrace().traceId()).isNull();
  }

  /** Counts drains; publishes nowhere. */
  private static final class FlushCountingPipeline implements EventPipeline {
    private int flushes;

    @Override
    public void publish(TraceEvent event) {
      // no consumer: this double exists to observe flush(), not to retain anything
    }

    @Override
    public void flush() {
      flushes++;
    }

    @Override
    public void close() {
      // nothing to release
    }
  }

  @Test
  void enterMethodReturnsNullWhenLevelIsOff() {
    var config = new NarrativeTraceConfig(TracingLevel.OFF);
    var context = new ThreadLocalNarrativeContext(config);

    SpanId spanId = context.enterMethod(new MethodSignature("Svc", "method", List.of()));
    assertThat(spanId).isNull();
  }

  @Test
  void enterMethodReturnsUniqueHexSpanIds() {
    var context = new ThreadLocalNarrativeContext();

    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "second", List.of()));
    SpanId h3 = context.enterMethod(new MethodSignature("Svc", "third", List.of()));

    assertThat(h1.value()).matches("[0-9a-f]{16}");
    assertThat(h2.value()).matches("[0-9a-f]{16}");
    assertThat(h3.value()).matches("[0-9a-f]{16}");
    assertThat(h1).isNotEqualTo(h2);
    assertThat(h2).isNotEqualTo(h3);

    context.exitMethodWithReturn("c");
    context.exitMethodWithReturn("b");
    context.exitMethodWithReturn("a");
  }

  @Test
  void emitTraceNodePreservesDuration() {
    var context = new ThreadLocalNarrativeContext();
    long duration = 5_000_000L;
    var node =
        new TraceNode(
            new MethodSignature("Svc", "emitted", List.of()),
            List.of(),
            new TraceOutcome.Returned("null"),
            duration,
            System.nanoTime(),
            null);

    context.emitTraceNode(node, null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).durationNanos()).isEqualTo(duration);
  }

  @Test
  void endScopeWithNullRemovesScopedParent() {
    var context = new ThreadLocalNarrativeContext();

    // Outer is still active (not exited) — peekActive returns h1
    context.enterMethod(new MethodSignature("Svc", "outer", List.of()));

    // Begin scope overriding parent to some other handle, then end with null
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "unrelated", List.of()));
    context.exitMethodWithReturn("ok");
    context.beginScope(h2);
    context.endScope(null);

    // After endScope(-1), scopedParent should be removed (null).
    // resolveParent should fall through to peekActive → h1.
    // If the mutant sets scopedParent to -1 instead of removing it,
    // resolveParent returns -1 → new call becomes a root instead of child of h1.
    context.enterMethod(new MethodSignature("Svc", "shouldNestUnderOuter", List.of()));
    context.exitMethodWithReturn("ok");
    context.exitMethodWithReturn("ok");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("outer");
    assertThat(root.children()).hasSize(2);
    assertThat(root.children().get(1).signature().methodName()).isEqualTo("shouldNestUnderOuter");
  }

  @Test
  void handleBasedReturnExitWithNullFallsBackToLifo() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "method", List.of()));

    // spanId == null should pop via LIFO
    context.exitMethodWithReturn("val", null);

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(((TraceOutcome.Returned) tree.roots().get(0).outcome()).renderedValue())
        .isEqualTo("val");
  }

  @Test
  void handleBasedReturnExitDetachesFromActiveStack() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "second", List.of()));

    context.exitMethodWithReturn("second-val", h2);

    // After handle-based exit, h2 should no longer be on the active stack.
    // New enter should nest under first, not second.
    context.enterMethod(new MethodSignature("Svc", "third", List.of()));
    context.exitMethodWithReturn("third-val");
    context.exitMethodWithReturn("first-val");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("first");
    // h2 and third should both be children of h1
    assertThat(root.children()).hasSize(2);
    assertThat(root.children().get(1).signature().methodName()).isEqualTo("third");
  }

  @Test
  void handleBasedExceptionExitDetachesFromActiveStack() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Svc", "first", List.of()));
    SpanId h2 = context.enterMethod(new MethodSignature("Svc", "second", List.of()));

    context.exitMethodWithException(new RuntimeException("err"), null, h2);

    // After handle-based exception exit, h2 should no longer be on the active stack.
    context.enterMethod(new MethodSignature("Svc", "third", List.of()));
    context.exitMethodWithReturn("third-val");
    context.exitMethodWithReturn("first-val");

    var tree = context.captureTrace();
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("first");
    assertThat(root.children()).hasSize(2);
    assertThat(root.children().get(1).signature().methodName()).isEqualTo("third");
  }

  @Test
  void runScopedReturnsSupplierValue() {
    var context = new ThreadLocalNarrativeContext();
    SpanId h1 = context.enterMethod(new MethodSignature("Svc", "outer", List.of()));
    context.exitMethodWithReturn("ok");

    String result = context.runScoped(h1, () -> "computed-value");

    assertThat(result).isEqualTo("computed-value");
  }

  @Test
  void snapshotActivationPreservesTraceId() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("ParentService", "handle", List.of()));
    context.exitMethodWithReturn("ok");

    TraceId parentTraceId = context.captureTrace().roots().get(0).spanContext().traceId();
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      TraceId childTraceId =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(new MethodSignature("ChildService", "work", List.of()));
                      context.exitMethodWithReturn("done");
                      return context.captureTrace().roots().get(0).spanContext().traceId();
                    }
                  })
              .get();

      assertThat(childTraceId).isEqualTo(parentTraceId);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void snapshotFirstChildParentIsCallerSpan() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    SpanId parentSpanId = context.enterMethod(new MethodSignature("Svc", "caller", List.of()));
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      SpanId childParentSpanId =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(new MethodSignature("Svc", "child", List.of()));
                      context.exitMethodWithReturn("ok");
                      return context.captureTrace().roots().get(0).spanContext().parentSpanId();
                    }
                  })
              .get();

      assertThat(childParentSpanId).isEqualTo(parentSpanId);
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void snapshotChildrenChainNormally() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("Svc", "caller", List.of()));
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(new MethodSignature("Svc", "outer", List.of()));
                      context.enterMethod(new MethodSignature("Svc", "inner", List.of()));
                      context.exitMethodWithReturn("inner-val");
                      context.exitMethodWithReturn("outer-val");
                      return context.captureTrace();
                    }
                  })
              .get();

      assertThat(childTree.roots()).hasSize(1);
      var outer = childTree.roots().get(0);
      assertThat(outer.signature().methodName()).isEqualTo("outer");
      assertThat(outer.children()).hasSize(1);
      assertThat(outer.children().get(0).signature().methodName()).isEqualTo("inner");
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void twoSnapshotsShareTraceId() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("Svc", "root", List.of()));
    var snapshot1 = context.snapshot();
    var snapshot2 = context.snapshot();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var f1 =
          executor.submit(
              () -> {
                try (var scope = snapshot1.activate()) {
                  context.enterMethod(new MethodSignature("Svc", "fork1", List.of()));
                  context.exitMethodWithReturn("ok");
                  return context.captureTrace().roots().get(0).spanContext().traceId();
                }
              });
      var f2 =
          executor.submit(
              () -> {
                try (var scope = snapshot2.activate()) {
                  context.enterMethod(new MethodSignature("Svc", "fork2", List.of()));
                  context.exitMethodWithReturn("ok");
                  return context.captureTrace().roots().get(0).spanContext().traceId();
                }
              });

      assertThat(f1.get()).isEqualTo(f2.get());
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void snapshotDoesNotAffectParentThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    context.enterMethod(new MethodSignature("Svc", "parent", List.of()));
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor
          .submit(
              () -> {
                try (var scope = snapshot.activate()) {
                  context.enterMethod(new MethodSignature("Svc", "child", List.of()));
                  context.exitMethodWithReturn("ok");
                }
              })
          .get();
    } finally {
      executor.shutdown();
    }

    // Parent thread continues normally — the child never touched the parent's active stack,
    // so afterFork still nests under parent rather than under the child.
    context.enterMethod(new MethodSignature("Svc", "afterFork", List.of()));
    context.exitMethodWithReturn("ok");
    context.exitMethodWithReturn("ok");

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("parent");
    // The snapshot was taken while parent was open, so the async child joins it as a child too.
    assertThat(root.children()).hasSize(2);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("child");
    assertThat(root.children().get(1).signature().methodName()).isEqualTo("afterFork");
  }

  @Test
  void setRequestContextStampsEvents() {
    var context = new ThreadLocalNarrativeContext();
    context.setRequestContext("GET", HttpRoute.of("/api/orders"), ClientIp.of("client-ip-1"));

    context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
    context.exitMethodWithReturn("ok");

    var sc = context.captureTrace().roots().get(0).spanContext();
    assertThat(sc.httpMethod()).isEqualTo("GET");
    assertThat(sc.httpRoute()).hasToString("/api/orders");
    assertThat(sc.clientIp()).hasToString("client-ip-1");
  }

  @Test
  void setUserContextStampsEvents() {
    var context = new ThreadLocalNarrativeContext();
    context.setUserContext(
        EnduserId.of("user-42"), SessionId.of("sess-abc"), TenantId.of("tenant-1"));

    context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
    context.exitMethodWithReturn("ok");

    var sc = context.captureTrace().roots().get(0).spanContext();
    assertThat(sc.enduserId()).hasToString("user-42");
    assertThat(sc.sessionId()).hasToString("sess-abc");
    assertThat(sc.tenantId()).hasToString("tenant-1");
  }

  @Test
  void resetClearsRequestContext() {
    var context = new ThreadLocalNarrativeContext();
    context.setRequestContext("POST", HttpRoute.of("/api/create"), ClientIp.of("client-ip-2"));
    context.setUserContext(EnduserId.of("user-1"), SessionId.of("sess-1"), TenantId.of("tenant-1"));
    context.reset();

    context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
    context.exitMethodWithReturn("ok");

    var sc = context.captureTrace().roots().get(0).spanContext();
    assertThat(sc.httpMethod()).isNull();
    assertThat(sc.httpRoute()).isNull();
    assertThat(sc.clientIp()).isNull();
    assertThat(sc.enduserId()).isNull();
    assertThat(sc.sessionId()).isNull();
    assertThat(sc.tenantId()).isNull();
  }

  @Test
  void requestContextPropagatesThroughSnapshot() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    context.setRequestContext("PUT", HttpRoute.of("/api/update"), ClientIp.of("client-ip-3"));
    context.enterMethod(new MethodSignature("Svc", "root", List.of()));
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childSc =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(new MethodSignature("Svc", "child", List.of()));
                      context.exitMethodWithReturn("ok");
                      return context.captureTrace().roots().get(0).spanContext();
                    }
                  })
              .get();

      assertThat(childSc.httpMethod()).isEqualTo("PUT");
      assertThat(childSc.httpRoute()).hasToString("/api/update");
      assertThat(childSc.clientIp()).hasToString("client-ip-3");
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void serviceIdentityStampedOnEverySpanContext() {
    var identity = new ServiceIdentity("order-svc", "1.0.0", "production");
    var context =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), defaultPipeline(), identity);
    context.enterMethod(new MethodSignature("Svc", "run", List.of()));
    context.exitMethodWithReturn("ok");

    var sc = context.captureTrace().roots().get(0).spanContext();
    assertThat(sc.serviceName()).isEqualTo("order-svc");
    assertThat(sc.serviceVersion()).isEqualTo("1.0.0");
    assertThat(sc.environment()).isEqualTo("production");
  }

  @Test
  void snapshotPreservesServiceIdentity() throws Exception {
    var identity = new ServiceIdentity("order-svc", "2.0.0", "staging");
    var context =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), defaultPipeline(), identity);
    context.enterMethod(new MethodSignature("Svc", "root", List.of()));
    var snapshot = context.snapshot();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var childSc =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(new MethodSignature("Svc", "child", List.of()));
                      context.exitMethodWithReturn("ok");
                      return context.captureTrace().roots().get(0).spanContext();
                    }
                  })
              .get();

      assertThat(childSc.serviceName()).isEqualTo("order-svc");
      assertThat(childSc.serviceVersion()).isEqualTo("2.0.0");
      assertThat(childSc.environment()).isEqualTo("staging");
    } finally {
      executor.shutdown();
    }
    context.exitMethodWithReturn("ok");
  }

  @Test
  void exitMethodWithReturnStructuredPreservesStructuredValue() {
    var ctx = new ThreadLocalNarrativeContext();
    var sig = new MethodSignature("Svc", "calc", List.of());
    ctx.enterMethod(sig);
    var structured = new ai.narrativetrace.api.event.RenderedValue.LongVal(42L);
    ctx.exitMethodWithReturn("42", structured, null);

    var tree = ctx.captureTrace();
    var outcome = (TraceOutcome.Returned) tree.roots().get(0).outcome();
    assertThat(outcome.structuredValue()).isEqualTo(structured);
  }

  @Test
  void exitMethodWithReturnStructuredWithSpanIdPreservesStructuredValue() {
    var ctx = new ThreadLocalNarrativeContext();
    var sig = new MethodSignature("Svc", "calc", List.of());
    var spanId = ctx.enterMethod(sig);
    var structured = new ai.narrativetrace.api.event.RenderedValue.DoubleVal(99.9);
    ctx.exitMethodWithReturn("99.9", structured, spanId);

    var tree = ctx.captureTrace();
    var outcome = (TraceOutcome.Returned) tree.roots().get(0).outcome();
    assertThat(outcome.structuredValue()).isEqualTo(structured);
  }

  private static ai.narrativetrace.core.pipeline.EventPipeline defaultPipeline() {
    return new ai.narrativetrace.core.pipeline.DualPathPipeline();
  }

  private void busyWait(long nanos) {
    long start = System.nanoTime();
    while (System.nanoTime() - start < nanos) {
      Thread.onSpinWait();
    }
  }

  // ── storyId / chapterId derivation ──────────────────────────────────────────

  @Test
  void storyIdDerivedFromFirstRootEnter() {
    var context = new ThreadLocalNarrativeContext();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());

    context.enterMethod(sig);
    assertThat(context.storyId()).isEqualTo("OrderService.placeOrder");

    context.exitMethodWithReturn(null);
  }

  @Test
  void chapterIdEqualsStoryIdForRootService() {
    var context = new ThreadLocalNarrativeContext();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());

    context.enterMethod(sig);
    assertThat(context.chapterId()).isEqualTo("OrderService.placeOrder");
    assertThat(context.chapterId()).isEqualTo(context.storyId());

    context.exitMethodWithReturn(null);
  }

  @Test
  void storyIdPropagatesToNestedSpans() {
    var context = new ThreadLocalNarrativeContext();
    var root = new MethodSignature("OrderService", "placeOrder", List.of());
    var child = new MethodSignature("InventoryService", "reserve", List.of());

    context.enterMethod(root);
    context.enterMethod(child);

    var tree = context.captureTrace();
    var rootNode = tree.roots().get(0);
    var childNode = rootNode.children().get(0);

    assertThat(rootNode.spanContext().storyId()).isEqualTo("OrderService.placeOrder");
    assertThat(childNode.spanContext().storyId()).isEqualTo("OrderService.placeOrder");

    context.exitMethodWithReturn(null);
    context.exitMethodWithReturn(null);
  }

  @Test
  void storyIdNotOverwrittenBySecondRootEnter() {
    var context = new ThreadLocalNarrativeContext();
    var first = new MethodSignature("OrderService", "placeOrder", List.of());
    var second = new MethodSignature("PaymentService", "charge", List.of());

    context.enterMethod(first);
    context.exitMethodWithReturn(null);
    context.enterMethod(second);
    context.exitMethodWithReturn(null);

    assertThat(context.storyId()).isEqualTo("OrderService.placeOrder");
  }

  @Test
  void resetClearsStoryIdAndChapterId() {
    var context = new ThreadLocalNarrativeContext();
    var sig = new MethodSignature("OrderService", "placeOrder", List.of());

    context.enterMethod(sig);
    context.exitMethodWithReturn(null);
    assertThat(context.storyId()).isEqualTo("OrderService.placeOrder");

    context.reset();
    assertThat(context.storyId()).isNull();
    assertThat(context.chapterId()).isNull();
  }

  @Test
  void storyIdNullBeforeAnyEnter() {
    var context = new ThreadLocalNarrativeContext();
    assertThat(context.storyId()).isNull();
    assertThat(context.chapterId()).isNull();
  }
}
