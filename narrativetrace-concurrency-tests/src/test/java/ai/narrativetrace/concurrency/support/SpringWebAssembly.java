/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.concurrency.shop.AsyncCheckout;
import ai.narrativetrace.concurrency.shop.AsyncInventory;
import ai.narrativetrace.concurrency.shop.Checkout;
import ai.narrativetrace.concurrency.shop.ExecutorCheckout;
import ai.narrativetrace.concurrency.shop.FireAndForgetCheckout;
import ai.narrativetrace.concurrency.shop.ForkCheckout;
import ai.narrativetrace.concurrency.shop.InMemoryAsyncInventory;
import ai.narrativetrace.concurrency.shop.InMemoryInventory;
import ai.narrativetrace.concurrency.shop.Inventory;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import ai.narrativetrace.spring.EnableNarrativeTrace;
import ai.narrativetrace.spring.web.NarrativeTraceWebConfiguration;
import io.micrometer.context.ContextRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The Spring Web production assembly on the PROXY route: a Spring context with
 * {@code @EnableNarrativeTrace} (the bean post-processor traces the beans), {@code @EnableAsync},
 * an executor decorated with the {@code ContextPropagatingTaskDecorator}, and the servlet filter
 * bean exactly as {@code NarrativeTraceWebConfiguration} builds it. Requests are served on the
 * caller's thread by invoking that filter bean, whose chain calls the Spring-managed checkout.
 */
public final class SpringWebAssembly implements AutoCloseable {

  private static final String HANDOVER_PROPERTY = "ntmt.handover";
  private static final String WORKERS_PROPERTY = "ntmt.workers";
  private static final AtomicReference<FireAndForgetCheckout> LAST_FIRE_AND_FORGET =
      new AtomicReference<>();

  private final AnnotationConfigApplicationContext spring =
      new AnnotationConfigApplicationContext();
  private final RecordingExporter exporter = new RecordingExporter();
  private final Overlap overlap = new Overlap();
  private final NarrativeTraceFilter filter;
  private final Checkout checkout;
  private final Optional<FireAndForgetCheckout> fireAndForget;

  private SpringWebAssembly(Handover handover, int workers) {
    spring
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "ntmt", Map.of(HANDOVER_PROPERTY, handover.name(), WORKERS_PROPERTY, workers)));
    spring.getBeanFactory().registerSingleton("recordingExporter", exporter);
    spring.register(Config.class);
    spring.refresh();
    filter = spring.getBean(NarrativeTraceFilter.class);
    checkout = spring.getBean(Checkout.class);
    fireAndForget =
        handover == Handover.FIRE_AND_FORGET
            ? Optional.of(LAST_FIRE_AND_FORGET.get())
            : Optional.empty();
  }

  public static SpringWebAssembly of(Handover handover) {
    return new SpringWebAssembly(handover, 4);
  }

  /** An assembly whose worker pools have exactly {@code workers} threads. */
  public static SpringWebAssembly of(Handover handover, int workers) {
    return new SpringWebAssembly(handover, workers);
  }

  public RecordingExporter exporter() {
    return exporter;
  }

  public FireAndForgetCheckout fireAndForget() {
    return fireAndForget.orElseThrow();
  }

  public void serve(String requestId) throws Exception {
    Requests.throughFilter(filter, overlap, checkout, requestId);
  }

  public void serveConcurrently(List<String> ids) {
    overlap.expect(ids.size());
    Requests.concurrently(ids, this::serve);
  }

  @Override
  public void close() {
    spring.close();
  }

  /** The application: what an adopter's configuration would declare. */
  @Configuration
  @EnableNarrativeTrace(basePackages = "ai.narrativetrace.concurrency.shop")
  @EnableAsync
  @Import(NarrativeTraceWebConfiguration.class)
  static class Config {

    @Bean
    ThreadLocalNarrativeContext narrativeContext() {
      var context =
          new ThreadLocalNarrativeContext(
              new NarrativeTraceConfig(), PipelineBootstrap.createDefault());
      ContextRegistry.getInstance()
          .registerThreadLocalAccessor(new NarrativeTraceThreadLocalAccessor(context));
      return context;
    }

    /** Also the {@code @Async} executor: Spring looks for the unique {@code TaskExecutor}. */
    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskExecutor taskExecutor(Environment environment) {
      var executor = new ThreadPoolTaskExecutor();
      executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
      executor.setCorePoolSize(environment.getRequiredProperty(WORKERS_PROPERTY, Integer.class));
      executor.setThreadFactory(Workers.factory());
      executor.initialize();
      return executor;
    }

    /** The plain pool the fork and fire-and-forget hand-overs submit wrapped tasks to. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService wrappedTaskPool(Environment environment) {
      return Executors.newFixedThreadPool(
          environment.getRequiredProperty(WORKERS_PROPERTY, Integer.class), Workers.factory());
    }

    @Bean
    Inventory inventory() {
      return new InMemoryInventory();
    }

    @Bean
    AsyncInventory asyncInventory() {
      return new InMemoryAsyncInventory();
    }

    @Bean
    Checkout checkout(Environment environment, ThreadLocalNarrativeContext context) {
      return switch (Handover.valueOf(environment.getRequiredProperty(HANDOVER_PROPERTY))) {
        case FORK_GROUP -> new ForkCheckout(context, wrappedTaskPool(environment), inventory());
        case FIRE_AND_FORGET -> fireAndForget(context, environment);
        case DECORATED_EXECUTOR -> new ExecutorCheckout(taskExecutor(environment), inventory());
        case ASYNC -> new AsyncCheckout(asyncInventory());
      };
    }

    private FireAndForgetCheckout fireAndForget(
        ThreadLocalNarrativeContext context, Environment environment) {
      var checkout = new FireAndForgetCheckout(context, wrappedTaskPool(environment), inventory());
      LAST_FIRE_AND_FORGET.set(checkout);
      return checkout;
    }
  }
}
