/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.concurrency.shop.Checkout;
import ai.narrativetrace.concurrency.shop.ExecutorCheckout;
import ai.narrativetrace.concurrency.shop.FireAndForgetCheckout;
import ai.narrativetrace.concurrency.shop.ForkCheckout;
import ai.narrativetrace.concurrency.shop.InMemoryInventory;
import ai.narrativetrace.concurrency.shop.Inventory;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.SoakProbe;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.spring.ContextPropagatingTaskDecorator;
import io.micrometer.context.ContextRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The servlet production assembly on the PROXY route: the real {@link NarrativeTraceFilter} over a
 * production-default context and pipeline, a proxied {@link Checkout} the "controller" calls, and a
 * shared worker pool every hand-over mechanism uses.
 */
public final class ServletAssembly implements AutoCloseable {

  public final ThreadLocalNarrativeContext context;
  public final EventPipeline pipeline;
  public final RecordingExporter exporter = new RecordingExporter();
  public final Overlap overlap = new Overlap();
  public final NarrativeTraceFilter filter;
  public final ExecutorService pool;
  public final Checkout checkout;
  public final Inventory inventory;

  /** The un-proxied fire-and-forget checkout, when that is the hand-over. */
  private final Optional<FireAndForgetCheckout> fireAndForget;

  private final Optional<ThreadPoolTaskExecutor> decorated;

  private ServletAssembly(
      Handover handover,
      int workers,
      Optional<TraceExporter> custom,
      Function<EventPipeline, ThreadLocalNarrativeContext> contextFactory) {
    pipeline = PipelineBootstrap.createDefault();
    context = contextFactory.apply(pipeline);
    filter = new NarrativeTraceFilter(context, custom.orElse(exporter));
    pool = Executors.newFixedThreadPool(workers, Workers.factory());
    inventory = NarrativeTraceProxy.trace(new InMemoryInventory(), Inventory.class, context);
    decorated =
        handover == Handover.DECORATED_EXECUTOR
            ? Optional.of(decoratedExecutor(workers))
            : Optional.empty();
    var implementation = implementation(handover, inventory);
    fireAndForget =
        implementation instanceof FireAndForgetCheckout ff ? Optional.of(ff) : Optional.empty();
    checkout = NarrativeTraceProxy.trace(implementation, Checkout.class, context);
  }

  /** The un-proxied fire-and-forget checkout; only for an assembly built with that hand-over. */
  public FireAndForgetCheckout fireAndForget() {
    return fireAndForget.orElseThrow();
  }

  /**
   * An assembly whose checkout hands its work over by {@code handover}, on {@code workers} threads.
   */
  public static ServletAssembly of(Handover handover, int workers) {
    return new ServletAssembly(handover, workers, Optional.empty(), ServletAssembly::production);
  }

  /**
   * As {@link #of(Handover, int)}, exporting to {@code custom} instead of the recording exporter.
   */
  public static ServletAssembly of(Handover handover, int workers, TraceExporter custom) {
    return new ServletAssembly(handover, workers, Optional.of(custom), ServletAssembly::production);
  }

  /**
   * As {@link #of(Handover, int, TraceExporter)}, over a context whose unowned-span limit is {@code
   * unownedLimit} (the B-211 seam), so a soak crosses the release boundary many times.
   */
  public static ServletAssembly withUnownedLimit(
      Handover handover, int workers, TraceExporter custom, int unownedLimit) {
    return new ServletAssembly(
        handover,
        workers,
        Optional.of(custom),
        pipeline ->
            SoakProbe.contextWithUnownedLimit(new NarrativeTraceConfig(), pipeline, unownedLimit));
  }

  private static ThreadLocalNarrativeContext production(EventPipeline pipeline) {
    return new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);
  }

  private Checkout implementation(Handover handover, Inventory inventory) {
    return switch (handover) {
      case FORK_GROUP -> new ForkCheckout(context, pool, inventory);
      case FIRE_AND_FORGET -> new FireAndForgetCheckout(context, pool, inventory);
      case DECORATED_EXECUTOR -> new ExecutorCheckout(decorated.orElseThrow(), inventory);
      case ASYNC -> throw new IllegalArgumentException("@Async needs a Spring context");
    };
  }

  private ThreadPoolTaskExecutor decoratedExecutor(int workers) {
    ContextRegistry.getInstance()
        .registerThreadLocalAccessor(new NarrativeTraceThreadLocalAccessor(context));
    var executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setCorePoolSize(workers);
    executor.setThreadFactory(Workers.factory());
    executor.initialize();
    return executor;
  }

  /** Serves one request through the filter; the "controller" is the lambda calling the checkout. */
  public void serve(String requestId) throws Exception {
    Requests.throughFilter(filter, overlap, checkout, requestId);
  }

  /**
   * As {@link #serve(String)}, with {@code alsoInChain} run in the same chain after the checkout.
   */
  public void serve(String requestId, Runnable alsoInChain) throws Exception {
    Requests.throughFilter(filter, overlap, checkout, requestId, alsoInChain);
  }

  /** As {@link #serve(String, Runnable)}, handing over work for {@code skus}. */
  public void serve(String requestId, java.util.List<String> skus, Runnable alsoInChain)
      throws Exception {
    Requests.throughFilter(filter, overlap, checkout, requestId, skus, alsoInChain);
  }

  /** N overlapping requests. */
  public void serveConcurrently(java.util.List<String> ids) {
    overlap.expect(ids.size());
    Requests.concurrently(ids, this::serve);
  }

  @Override
  public void close() {
    pool.shutdownNow();
    decorated.ifPresent(
        executor -> {
          executor.shutdown();
          ContextRegistry.getInstance()
              .removeThreadLocalAccessor(NarrativeTraceThreadLocalAccessor.KEY);
        });
    context.reset();
    pipeline.close();
  }
}
