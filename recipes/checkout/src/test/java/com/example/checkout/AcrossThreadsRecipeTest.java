/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/AcrossThreadsRecipeTest.java
package com.example.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves documentation/recipes/follow-a-flow-across-threads.md: the two tasks the checkout hands to
 * an executor stay inside the caller's trace when they are wrapped, and a task submitted without
 * the handover starts a trace of its own (rule 8, docs as tests).
 *
 * <p><b>@llmNote</b> One worker thread, named: the page embeds a real run's log byte for byte, and
 * two threads logging at once interleave differently every time. The handover is identical either
 * way — what a second thread would add is nondeterministic output, not a second mechanism. The
 * Gradle task {@code recipeAcrossThreadsLog} names the first test METHOD, not this class: the
 * second one's trace id is freshly generated on every run, which is the finding it reports and the
 * one thing no embedded block can hold.
 */
@ExtendWith(NarrativeTraceExtension.class)
class AcrossThreadsRecipeTest {

  /** A fixed W3C traceparent, so the page's embedded artifacts name the same trace every run. */
  static final String DEMO_TRACEPARENT = "00-c4e1d0a7c4e1d0a7c4e1d0a7c4e1d0a7-c4e1d0a7c4e1d0a7-01";

  private final ExecutorService workers =
      Executors.newSingleThreadExecutor(AcrossThreadsRecipeTest::checkoutWorker);

  @AfterEach
  void stopTheWorker() {
    workers.shutdownNow();
  }

  @Test
  void the_background_tasks_stay_in_the_customers_trace(NarrativeContext context) {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var repository = new InMemoryOrderRepository();
    var checkout = parallelCheckout(context, stock, repository);

    var orderId = checkout.placeOrder("C-1234", "SKU-KB", 2, "4242424242424242");

    assertThat(orderId).isEqualTo("ORD-C-1234-SKU-KB-2");
    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(repository.saved()).hasSize(1);
    assertTheChildrenCameHome(context);
  }

  /** The merged children are under the span that launched them, in the caller's own trace. */
  private static void assertTheChildrenCameHome(NarrativeContext context) {
    var root = context.captureTrace().roots().get(0);
    assertThat(names(root.children()))
        .containsExactly(
            "InventoryService.reserve",
            "FraudCheckService.approve",
            "PaymentService.charge",
            "OrderRepository.save");
    var reserve = root.children().get(0);
    assertThat(reserve.spanContext().traceId()).isEqualTo(root.spanContext().traceId());
    assertThat(reserve.spanContext().parentSpanId()).isEqualTo(root.spanContext().spanId());
    assertThat(reserve.concurrency().threadName()).isEqualTo("checkout-worker-1");
  }

  @Test
  void a_task_submitted_without_the_handover_starts_a_new_trace(NarrativeContext context)
      throws InterruptedException, ExecutionException {
    context.adoptTraceparent(Traceparent.parse(DEMO_TRACEPARENT));
    var stock = new InMemoryInventory("SKU-KB", 5);
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var onTheWorker = new AtomicReference<TraceId>();

    // No ForkGroup, no snapshot: the task is handed over, the trace is not.
    workers
        .submit(
            () -> {
              inventory.reserve("SKU-KB", 2);
              onTheWorker.set(context.traceId());
            })
        .get();

    assertThat(stock.availableUnits("SKU-KB")).isEqualTo(3);
    assertThat(onTheWorker.get()).isNotNull().isNotEqualTo(context.traceId());
    assertThat(names(context.captureTrace().roots())).doesNotContain("InventoryService.reserve");
  }

  private OrderService parallelCheckout(
      NarrativeContext context, InMemoryInventory stock, InMemoryOrderRepository repository) {
    var inventory = NarrativeTraceProxy.trace(stock, InventoryService.class, context);
    var fraud =
        NarrativeTraceProxy.trace(new RuleBasedFraudCheck(), FraudCheckService.class, context);
    var payments =
        NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
    var orders = NarrativeTraceProxy.trace(repository, OrderRepository.class, context);
    return NarrativeTraceProxy.trace(
        new ParallelCheckout(context, inventory, fraud, payments, orders, workers),
        OrderService.class,
        context);
  }

  private static List<String> names(List<TraceNode> nodes) {
    return nodes.stream()
        .map(node -> node.signature().className() + "." + node.signature().methodName())
        .toList();
  }

  /** A named daemon thread, so the page's embedded output names the same thread every run. */
  private static Thread checkoutWorker(Runnable runnable) {
    var thread = new Thread(runnable, "checkout-worker-1");
    thread.setDaemon(true);
    return thread;
  }
}
