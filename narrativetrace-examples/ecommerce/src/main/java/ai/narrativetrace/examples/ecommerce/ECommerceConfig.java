/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import io.micrometer.context.ContextRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring configuration for the e-commerce tutorial.
 *
 * <p>This class is meant to be read alongside {@link ECommerceExample}. It shows the minimum
 * infrastructure needed to make the example interesting:
 *
 * <ul>
 *   <li>A shared {@link NarrativeContext} backed by SLF4J event logging.
 *   <li>Micrometer context propagation so async work keeps the NarrativeTrace lineage.
 *   <li>A task executor with a decorator that propagates both MDC and the narrative context.
 *   <li>Simple in-memory service beans that keep the tutorial easy to follow.
 * </ul>
 *
 * <p>INTENT: Use this as a reference when wiring a small Spring application for proxy-based
 * NarrativeTrace capture with async execution.
 */
@Configuration
@EnableNarrativeTrace
@EnableAsync
public class ECommerceConfig {

  @Bean
  NarrativeContext narrativeContext() {
    var pipeline = new DualPathPipeline(new Slf4jTraceEventListener());
    var tlc = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
    var accessor = new NarrativeTraceThreadLocalAccessor(tlc);
    ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
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
  NotificationService notificationService(NarrativeContext narrativeContext) {
    return new JsonPlaceholderNotificationService(narrativeContext);
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
