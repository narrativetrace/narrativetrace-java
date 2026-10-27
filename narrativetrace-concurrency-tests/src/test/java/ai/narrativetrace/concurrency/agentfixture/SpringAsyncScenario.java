/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.agentfixture;

import ai.narrativetrace.concurrency.shop.AsyncCheckout;
import ai.narrativetrace.concurrency.shop.AsyncInventory;
import ai.narrativetrace.concurrency.shop.Checkout;
import ai.narrativetrace.concurrency.shop.InMemoryAsyncInventory;
import ai.narrativetrace.concurrency.support.Overlap;
import ai.narrativetrace.concurrency.support.Requests;
import ai.narrativetrace.concurrency.support.Workers;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import ai.narrativetrace.spring.web.NarrativeTraceWebConfiguration;
import io.micrometer.context.ContextRegistry;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Spring Web on the AGENT route: no {@code @EnableNarrativeTrace} and no proxy — the shop beans are
 * plain objects the agent weaves. The context bean is the agent's own shared context, the filter
 * bean is the one {@code NarrativeTraceWebConfiguration} builds, and {@code @Async} runs on an
 * executor decorated with the {@code ContextPropagatingTaskDecorator}.
 */
final class SpringAsyncScenario {

  private static NarrativeContext agentContext;
  private static FileExporter fileExporter;

  private SpringAsyncScenario() {}

  static void run(NarrativeContext context, FileExporter exporter) throws Exception {
    agentContext = context;
    fileExporter = exporter;
    var overlap = new Overlap();
    try (var spring = start(4)) {
      var filter = spring.getBean(NarrativeTraceFilter.class);
      var checkout = spring.getBean(Checkout.class);
      AgentFixtureMain.runPhases(
          exporter, overlap, id -> Requests.throughFilter(filter, overlap, checkout, id));
    }
    try (var spring = start(1)) {
      var filter = spring.getBean(NarrativeTraceFilter.class);
      var checkout = spring.getBean(Checkout.class);
      AgentFixtureMain.runReuse(
          exporter, id -> Requests.throughFilter(filter, overlap, checkout, id));
    }
  }

  private static AnnotationConfigApplicationContext start(int workers) {
    var spring = new AnnotationConfigApplicationContext();
    spring
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("ntmt", Map.of("ntmt.workers", workers)));
    spring.register(Config.class);
    spring.refresh();
    return spring;
  }

  @Configuration
  @EnableAsync
  @Import(NarrativeTraceWebConfiguration.class)
  static class Config {

    @Bean
    NarrativeContext narrativeContext() {
      ContextRegistry.getInstance()
          .registerThreadLocalAccessor(
              new NarrativeTraceThreadLocalAccessor((ThreadLocalNarrativeContext) agentContext));
      return agentContext;
    }

    @Bean
    ai.narrativetrace.api.export.TraceExporter traceExporter() {
      return fileExporter;
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskExecutor taskExecutor(Environment environment) {
      var executor = new ThreadPoolTaskExecutor();
      executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
      executor.setCorePoolSize(environment.getRequiredProperty("ntmt.workers", Integer.class));
      executor.setThreadFactory(Workers.factory());
      executor.initialize();
      return executor;
    }

    @Bean
    AsyncInventory asyncInventory() {
      return new InMemoryAsyncInventory();
    }

    @Bean
    Checkout checkout() {
      return new AsyncCheckout(asyncInventory());
    }

    @Bean
    ClosingRegistry registryCleanup() {
      return new ClosingRegistry();
    }
  }

  /** Removes the accessor this context registered when the context closes. */
  static class ClosingRegistry implements AutoCloseable {
    @Override
    public void close() {
      ContextRegistry.getInstance()
          .removeThreadLocalAccessor(NarrativeTraceThreadLocalAccessor.KEY);
    }
  }
}
