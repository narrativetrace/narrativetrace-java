/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;
import ai.narrativetrace.examples.AsciiSequenceDiagram;
import ai.narrativetrace.examples.DemoTraces;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Tutorial runner for the e-commerce example.
 *
 * <p>This class is written as a guided tour, not as an ordinary production main method. Running it
 * walks through a sequence of scenarios that demonstrate what NarrativeTrace looks like in a
 * realistic Spring application.
 *
 * <h2>What you will see</h2>
 *
 * <ul>
 *   <li>A successful order placement that fans out into async notification work.
 *   <li>A payment failure where the trace exposes an inventory-release bug.
 *   <li>A flaky external dependency that succeeds once and then fails.
 *   <li>Input-validation style failures such as unknown customer and out-of-stock requests.
 *   <li>An explicit async example showing separate main-thread and worker-thread captures.
 * </ul>
 *
 * <h2>How to use this tutorial</h2>
 *
 * <ol>
 *   <li>Run the main method and read the indented trace first.
 *   <li>Compare the prose and Mermaid renderings for the same scenario.
 *   <li>Open {@link ECommerceConfig} to see how the trace context, logging pipeline, and async
 *       propagation are wired.
 *   <li>Open {@link DefaultOrderService} to connect the orchestration code to the trace you just
 *       saw.
 * </ol>
 *
 * <p>Correlation note: in a real Spring Boot app, Micrometer Tracing would usually supply a
 * request-bound {@code traceId} in MDC. This demo simulates that with sequential IDs because there
 * is no inbound HTTP request.
 *
 * <p>INTENT: Start here if you want to understand the "whole system" developer experience of
 * NarrativeTrace rather than just one isolated API.
 */
public class ECommerceExample {

  private static final Logger logger = LoggerFactory.getLogger(ECommerceExample.class);
  private static final AtomicInteger traceCounter = new AtomicInteger(1);
  private static String currentScenario = "";

  private static void beginTrace(String label) {
    currentScenario = label;
    MDC.put("traceId", "trace-%03d".formatted(traceCounter.getAndIncrement()));
    logger.info("=== {} ===\n", label);
  }

  private static void endTrace() {
    MDC.remove("traceId");
  }

  /** Announces and prints the indented-tree rendering of an already-captured trace. */
  private static void logTraceTree(TraceTree trace) {
    DemoTraces.capture(currentScenario, trace);
    logger.info("\n--- Trace tree ---\n");
    logger.info("\n{}", new IndentedTextRenderer().render(trace));
  }

  /** Renders the same trace as PlantUML markup, then draws it as Unicode text for the console. */
  private static void logAsciiSequenceDiagram(TraceTree trace) {
    logger.info("\n--- Sequence diagram (ASCII) ---\n");
    logger.info(
        "\n{}", AsciiSequenceDiagram.render(new PlantUmlSequenceDiagramRenderer().render(trace)));
  }

  public static void main(String[] args) {
    try (var spring = new AnnotationConfigApplicationContext(ECommerceConfig.class)) {
      var context = spring.getBean(NarrativeContext.class);
      var orders = spring.getBean(OrderService.class);
      var notifications = spring.getBean(NotificationService.class);
      var catalog = spring.getBean(ProductCatalogService.class);
      var executor = spring.getBean(ThreadPoolTaskExecutor.class);

      runSuccessfulOrder(context, orders, notifications);
      runPaymentFailure(context, orders);
      runFlakyService(context);
      runUnknownCustomer(context, orders);
      runOutOfStock(context, orders);
      runExplicitAsync(context, catalog, executor);

      executor.shutdown();
    }
  }

  private static void runSuccessfulOrder(
      NarrativeContext context, OrderService orders, NotificationService notifications) {
    beginTrace("Scenario 1: Successful Order + Async Notification");
    var result = orders.placeOrder("C-1234", "SKU-MECHANICAL-KB", 2);
    notifications.notifyOrderPlaced("C-1234", result.orderId()).join();
    var trace = context.captureTrace();
    logTraceTree(trace);
    logger.info("\n--- Prose ---\n");
    logger.info("\n{}", new ProseRenderer().render(trace));
    logger.info("\n--- Mermaid ---\n");
    logger.info("\n{}", new MermaidSequenceDiagramRenderer().render(trace));
    endTrace();
  }

  private static void runPaymentFailure(NarrativeContext context, OrderService orders) {
    context.reset();
    beginTrace("Scenario 2: Payment Failure — Inventory Leak Bug");
    try {
      orders.placeOrder("C-BROKE", "SKU-MOUSE-PAD", 3);
    } catch (PaymentDeclinedException e) {
      // expected
    }
    var trace = context.captureTrace();
    logTraceTree(trace);
    logger.info(
        "\n  ^ Notice: InventoryService.reserve was called but InventoryService.release is missing from the trace.");
    logger.info("\n--- Prose ---\n");
    logger.info("\n{}", new ProseRenderer().render(trace));
    logger.info("\n--- Mermaid ---\n");
    logger.info("\n{}", new MermaidSequenceDiagramRenderer().render(trace));
    endTrace();
  }

  private static void runFlakyService(NarrativeContext context) {
    context.reset();
    beginTrace("Scenario 3: Flaky External Service");
    var flakyNotifications =
        NarrativeTraceProxy.trace(
            new FlakyNotificationService(new JsonPlaceholderNotificationService(context), 2),
            NotificationService.class,
            context);
    flakyNotifications.notifyOrderPlaced("C-1234", "ORD-00001");
    try {
      flakyNotifications.notifyOrderPlaced("C-1234", "ORD-00002");
    } catch (ExternalServiceException e) {
      // expected
    }
    var trace = context.captureTrace();
    logTraceTree(trace);
    logger.info("\n--- Prose ---\n");
    logger.info("\n{}", new ProseRenderer().render(trace));
    endTrace();
  }

  private static void runUnknownCustomer(NarrativeContext context, OrderService orders) {
    context.reset();
    beginTrace("Scenario 4: Unknown Customer");
    try {
      orders.placeOrder("C-UNKNOWN", "SKU-MECHANICAL-KB", 1);
    } catch (IllegalArgumentException e) {
      // expected
    }
    var trace = context.captureTrace();
    logTraceTree(trace);
    logger.info("\n--- Prose ---\n");
    logger.info("\n{}", new ProseRenderer().render(trace));
    endTrace();
  }

  private static void runOutOfStock(NarrativeContext context, OrderService orders) {
    context.reset();
    beginTrace("Scenario 5: Out of Stock");
    try {
      orders.placeOrder("C-1234", "SKU-USB-HUB", 9999);
    } catch (IllegalStateException e) {
      // expected
    }
    var trace = context.captureTrace();
    logTraceTree(trace);
    logger.info("\n--- Prose ---\n");
    logger.info("\n{}", new ProseRenderer().render(trace));
    logAsciiSequenceDiagram(trace);
    endTrace();
  }

  private static void runExplicitAsync(
      NarrativeContext context, ProductCatalogService catalog, ThreadPoolTaskExecutor executor) {
    context.reset();
    beginTrace("Scenario 6: Explicit Async Trace Capture");
    logger.info(
        "  Traces are thread-scoped: captureTrace() returns only the calling thread's story.");
    logger.info(
        "  That is why two trees follow — the async lambda captured its own calls on the worker thread and handed the tree back.\n");
    var renderer = new IndentedTextRenderer();

    catalog.lookupPrice("SKU-MECHANICAL-KB");
    var mainTrace = context.captureTrace();

    var asyncTrace =
        CompletableFuture.supplyAsync(
                () -> {
                  catalog.lookupPrice("SKU-MOUSE-PAD");
                  catalog.lookupPrice("SKU-USB-HUB");
                  return context.captureTrace();
                },
                executor)
            .join();

    DemoTraces.capture("Scenario 6: Explicit Async Trace Capture (main thread)", mainTrace);
    DemoTraces.capture("Scenario 6: Explicit Async Trace Capture (async thread)", asyncTrace);
    logger.info("Main thread trace:\n{}", renderer.render(mainTrace));
    logger.info("\nAsync thread trace:\n{}", renderer.render(asyncTrace));
    endTrace();
  }
}
