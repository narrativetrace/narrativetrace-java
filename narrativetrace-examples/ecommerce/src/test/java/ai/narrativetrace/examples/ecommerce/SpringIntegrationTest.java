/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import io.micrometer.context.ContextRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SpringIntegrationTest {

  @Test
  void springAutoWrappedBeansProduceTrace() {
    try (var spring = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var context = spring.getBean(NarrativeContext.class);
      var orders = spring.getBean(OrderService.class);

      context.reset();
      orders.placeOrder("C-1234", "SKU-MECHANICAL-KB", 2);

      var narrative = new IndentedTextRenderer().render(context.captureTrace());
      assertThat(narrative).contains("OrderService.placeOrder");
      assertThat(narrative).contains("CustomerService.findCustomer");
      assertThat(narrative).contains("ProductCatalogService.lookupPrice");
      assertThat(narrative).contains("InventoryService.reserve");
      assertThat(narrative).contains("PaymentService.charge");
    }
  }

  @Test
  void asyncCallRunsOnExecutorThread() throws Exception {
    try (var spring = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var notifications = spring.getBean(NotificationService.class);

      TestConfig.ASYNC_THREAD_NAME.set(null);
      var result = notifications.notifyOrderPlaced("C-1234", "ORD-001").get(5, SECONDS);

      assertThat(result).isTrue();
      assertThat(TestConfig.ASYNC_THREAD_NAME.get()).startsWith("async-notify-");
    }
  }

  @Test
  void asyncCallAppearsInTheCallersTrace() throws Exception {
    try (var spring = new AnnotationConfigApplicationContext(TestConfig.class)) {
      var context = spring.getBean(ThreadLocalNarrativeContext.class);
      var orders = spring.getBean(OrderService.class);
      var notifications = spring.getBean(NotificationService.class);

      var order = orders.placeOrder("C-1234", "SKU-MECHANICAL-KB", 2);
      notifications.notifyOrderPlaced("C-1234", order.orderId()).get(5, SECONDS);
      var tree = context.captureTrace();
      var narrative = new IndentedTextRenderer().render(tree);

      // The async call is narrated on the worker thread; the caller's captured tree must show it
      // too, or the two halves of the dual path disagree about what happened. Spring completes the
      // future inside the decorated task, so this capture routinely runs while the decorator's
      // scope is still open — the worker's spans have to be reportable from publication, not from
      // scope close. The deterministic pin for that window is
      // core's LiveSnapshotScopeVisibilityTest.
      assertThat(narrative).contains("OrderService.placeOrder");
      assertThat(narrative).contains("NotificationService.notifyOrderPlaced");

      var asyncCalls = nodesNamed(tree.roots(), "notifyOrderPlaced");
      assertThat(asyncCalls)
          .as("reported once: the live scope and its closing adoption offer the same spans")
          .hasSize(1);
      assertThat(asyncCalls.get(0).spanContext().traceId())
          .as("the async call joins the caller's trace rather than starting one of its own")
          .isEqualTo(nodesNamed(tree.roots(), "placeOrder").get(0).spanContext().traceId());
    }
  }

  private static List<TraceNode> nodesNamed(List<TraceNode> nodes, String methodName) {
    var matches = new ArrayList<TraceNode>();
    for (TraceNode node : nodes) {
      if (node.signature().methodName().equals(methodName)) {
        matches.add(node);
      }
      matches.addAll(nodesNamed(node.children(), methodName));
    }
    return matches;
  }

  @Configuration
  @EnableNarrativeTrace
  @EnableAsync
  static class TestConfig {

    static final AtomicReference<String> ASYNC_THREAD_NAME = new AtomicReference<>();

    @Bean
    NarrativeContext narrativeContext() {
      var tlc = new ThreadLocalNarrativeContext();
      ContextRegistry.getInstance()
          .registerThreadLocalAccessor(new NarrativeTraceThreadLocalAccessor(tlc));
      return tlc;
    }

    @Bean
    ThreadPoolTaskExecutor taskExecutor() {
      var executor = new ThreadPoolTaskExecutor();
      executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
      executor.setCorePoolSize(2);
      executor.setThreadNamePrefix("async-notify-");
      executor.initialize();
      return executor;
    }

    @Bean
    CustomerService customerService() {
      return new InMemoryCustomerService();
    }

    @Bean
    ProductCatalogService catalogService() {
      return new InMemoryProductCatalogService();
    }

    @Bean
    InventoryService inventoryService() {
      return new InMemoryInventoryService();
    }

    @Bean
    PaymentService paymentService() {
      return new InMemoryPaymentService();
    }

    @Bean
    NotificationService notificationService() {
      return (customerId, orderId) -> {
        ASYNC_THREAD_NAME.set(Thread.currentThread().getName());
        return CompletableFuture.completedFuture(true);
      };
    }

    @Bean
    OrderService orderService(
        CustomerService customerService,
        ProductCatalogService catalogService,
        InventoryService inventoryService,
        PaymentService paymentService) {
      return new DefaultOrderService(
          customerService, catalogService, inventoryService, paymentService);
    }
  }
}
