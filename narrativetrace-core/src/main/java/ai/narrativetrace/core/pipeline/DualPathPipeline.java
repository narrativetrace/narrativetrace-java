/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Simple fan-out pipeline with one synchronous path and one optional best-effort path.
 *
 * <p>INTENT: This is the default composition point for attaching immediate listeners while still
 * retaining published events for later tree capture. The retention contract is {@link
 * RetainingConsumer}; {@link BufferedEventConsumer} is the default implementation.
 *
 * <p><b>@llmNote</b> The synchronous listener runs inline on the caller thread. Keep it fast and
 * non-blocking if capture latency matters.
 *
 * <p><b>@sideEffects</b> {@link #publish} isolates each path: a listener or consumer that throws
 * <em>anything</em>, {@link Error} included, is swallowed, never propagates into application code,
 * and never prevents the other path from receiving the event. This guarantee is load-bearing for
 * deferred async exits, where an exception escaping the exit callback would leave the caller's
 * future incomplete forever. Both paths go through {@link TraceBoundary}, which is where the
 * catch-{@code Throwable} rule lives.
 */
public final class DualPathPipeline implements EventPipeline {

  private final Consumer<TraceEvent> synchronousListener;
  private final Consumer<TraceEvent> bestEffortConsumer;

  /**
   * Creates the default core topology: no synchronous listener, events retained by a {@link
   * BufferedEventConsumer} with the default capacity and on-demand draining.
   *
   * <p>INTENT: The one canonical construction of "capture works, narration off" — contexts and
   * integrations use this instead of assembling the default store themselves.
   *
   * <p><b>@edgeCase</b> This constructor reads no configuration: {@code
   * narrativetrace.buffer.capacity} is resolved by {@link PipelineBootstrap}, which is what a
   * context builds when you do not hand it a pipeline. Constructing the topology by hand means
   * choosing the ring size by hand too — pass {@code new BufferedEventConsumer(capacity, false)} as
   * the best-effort consumer.
   */
  public DualPathPipeline() {
    this(null, defaultRetention());
  }

  /**
   * Creates the common capturing topology: the listener runs synchronously on the durable path, and
   * events are additionally retained by a default {@link BufferedEventConsumer}.
   *
   * <p>INTENT: The terse form for the common case — narration plus working {@code captureTrace()}.
   * For a topology that deliberately retains nothing, use {@link #narrationOnly(Consumer)}.
   */
  public DualPathPipeline(Consumer<TraceEvent> synchronousListener) {
    this(synchronousListener, defaultRetention());
  }

  /**
   * Creates a narration-only topology: the listener runs synchronously, nothing is retained.
   *
   * <p>INTENT: Deliberate choice for deployments that want durable narrative logging and never
   * query — {@code captureTrace()} over this pipeline sees no events, by design. Named loudly so
   * the no-capture topology can never be constructed by accident.
   *
   * @param synchronousListener Listener for the durable inline path. Never null here — a pipeline
   *     with neither listener nor retention would do nothing.
   * @return A pipeline that narrates and retains nothing.
   */
  public static DualPathPipeline narrationOnly(Consumer<TraceEvent> synchronousListener) {
    return new DualPathPipeline(synchronousListener, null);
  }

  private static RetainingConsumer defaultRetention() {
    return new BufferedEventConsumer(BufferedEventConsumer.DEFAULT_CAPACITY, false);
  }

  public DualPathPipeline(
      Consumer<TraceEvent> synchronousListener, Consumer<TraceEvent> bestEffortConsumer) {
    this.synchronousListener = synchronousListener;
    this.bestEffortConsumer = bestEffortConsumer;
  }

  @Override
  public void publish(TraceEvent event) {
    TraceBoundary.deliver(synchronousListener, event);
    TraceBoundary.deliver(bestEffortConsumer, event);
  }

  /** Returns whether the best-effort path retains events for later retrieval. */
  public boolean retainsEvents() {
    return bestEffortConsumer instanceof RetainingConsumer;
  }

  /**
   * Returns whatever holds the best-effort slot.
   *
   * <p>INTENT: Lets composition tests assert the topology's <em>shape</em>, not just its behaviour
   * — specifically that the default path allocates no extra indirection when nothing is discovered.
   * Package-private: the slot's identity is an internal detail, not part of the pipeline contract.
   */
  Consumer<TraceEvent> bestEffortConsumer() {
    return bestEffortConsumer;
  }

  @Override
  public long droppedEventCount() {
    return bestEffortConsumer instanceof BufferedEventConsumer buffered
        ? buffered.droppedCount()
        : 0L;
  }

  @Override
  public void flush() {
    if (bestEffortConsumer instanceof RetainingConsumer retained) {
      retained.flush();
    }
  }

  @Override
  public boolean drained() {
    return !(bestEffortConsumer instanceof RetainingConsumer retained) || retained.drained();
  }

  @Override
  public List<TraceEvent> events() {
    if (bestEffortConsumer instanceof RetainingConsumer retained) {
      return retained.events();
    }
    return List.of();
  }

  @Override
  public void clear() {
    if (bestEffortConsumer instanceof RetainingConsumer retained) {
      retained.clear();
    }
  }

  /**
   * Removes only the retained events belonging to the given span ids.
   *
   * <p>INTENT: Request-scoped cleanup — a finishing request drops its own consumed events without
   * disturbing other in-flight traces sharing this pipeline.
   */
  @Override
  public void clearSpans(Set<SpanId> spanIds) {
    if (bestEffortConsumer instanceof RetainingConsumer retained) {
      retained.removeSpans(spanIds);
    }
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // close failure is not fatal, of any kind
  public void close() {
    if (bestEffortConsumer instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Throwable t) { // NOPMD
        // Best-effort — close failure is not fatal
      }
    }
  }
}
