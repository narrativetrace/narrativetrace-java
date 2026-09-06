/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

/**
 * The hot path the pipeline strategy documents argue about, finally measured.
 *
 * <p>INTENT: Nothing measured {@code DualPathPipeline.publish} or the ring underneath it, so every
 * claim about the buffered path's cost — and every comparison against an LMAX ring in the Pro tier
 * — rested on reasoning rather than numbers. This class supplies the core-side half. The Disruptor
 * comparison stays in Pro, because that dependency must not enter this repository.
 *
 * <p>It lives in {@code ai.narrativetrace.core.pipeline} on purpose: {@link BoundedEventBuffer} is
 * package-private, and measuring the ring through a public wrapper would measure the wrapper. The
 * jcstress module places its buffer tests in the same package for the same reason.
 *
 * <p><b>@llmNote</b> The topology benchmarks never drain, so the {@code EventStore} behind the
 * best-effort path stays empty and nothing grows during a run: {@code publish} reaches {@code
 * BoundedEventBuffer.put} and stops there, which is exactly the caller-visible cost.
 *
 * <p><b>@edgeCase</b> {@code bufferOffer_shedding} is the load-shed regime seen from the producer:
 * a 16-slot ring saturates within the first microsecond of warm-up, so every measured op overwrites
 * the oldest slot. Comparing it with {@code bufferOffer_1thread} answers whether overflow is a
 * cliff — the ring is designed so that it is not, and this is where that stops being an assertion.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class PipelineOverheadBenchmark {

  private static final MethodSignature SIGNATURE =
      new MethodSignature("PipelineBenchmarkService", "publish", List.of());

  /** Slots for the saturated ring: small enough that every measured op is an overwrite. */
  private static final int SHEDDING_CAPACITY = 16;

  private static TraceEvent event() {
    var spanContext =
        SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
    return new TraceEvent.EnterEvent(spanContext, System.nanoTime(), SIGNATURE);
  }

  /** The four topologies a deployment can choose, each holding one pre-built event to publish. */
  @State(Scope.Thread)
  public static class Topologies {

    TraceEvent event;
    DualPathPipeline inert;
    DualPathPipeline capturing;
    DualPathPipeline narrating;
    DualPathPipeline narratingAndCapturing;

    /** Written by the synchronous listener so its work cannot be optimized away. */
    public TraceEvent lastNarrated;

    @Setup(Level.Trial)
    public void setup() {
      event = event();
      inert = new DualPathPipeline(null, null);
      capturing = new DualPathPipeline();
      narrating = DualPathPipeline.narrationOnly(received -> lastNarrated = received);
      narratingAndCapturing =
          new DualPathPipeline(
              received -> lastNarrated = received,
              new BufferedEventConsumer(BufferedEventConsumer.DEFAULT_CAPACITY, false));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      inert.close();
      capturing.close();
      narrating.close();
      narratingAndCapturing.close();
    }
  }

  /** Rings shared across threads, so {@code @Threads} measures real producer contention. */
  @State(Scope.Benchmark)
  public static class Rings {

    TraceEvent event;
    BoundedEventBuffer roomy;
    BoundedEventBuffer saturated;

    @Setup(Level.Trial)
    public void setup() {
      event = event();
      roomy = new BoundedEventBuffer(BufferedEventConsumer.DEFAULT_CAPACITY);
      saturated = new BoundedEventBuffer(SHEDDING_CAPACITY);
    }
  }

  /** The floor: a pipeline with neither path, i.e. two null checks. */
  @Benchmark
  public void publish_noConsumers(Topologies state) {
    state.inert.publish(state.event);
  }

  /** The default topology — retention only, no narration. */
  @Benchmark
  public void publish_capturing(Topologies state) {
    state.capturing.publish(state.event);
  }

  /** Narration-only: the durable inline path, nothing retained. */
  @Benchmark
  public void publish_narrationOnly(Topologies state, Blackhole blackhole) {
    state.narrating.publish(state.event);
    blackhole.consume(state.lastNarrated);
  }

  /** Both paths, which is what a deployment with the SLF4J module on the classpath gets. */
  @Benchmark
  public void publish_narratingAndCapturing(Topologies state, Blackhole blackhole) {
    state.narratingAndCapturing.publish(state.event);
    blackhole.consume(state.lastNarrated);
  }

  @Benchmark
  @Threads(1)
  public void bufferOffer_1thread(Rings state) {
    state.roomy.put(state.event);
  }

  @Benchmark
  @Threads(4)
  public void bufferOffer_4threads(Rings state) {
    state.roomy.put(state.event);
  }

  @Benchmark
  @Threads(16)
  public void bufferOffer_16threads(Rings state) {
    state.roomy.put(state.event);
  }

  /** The same offer into a ring that is permanently full: the load-shed branch. */
  @Benchmark
  @Threads(1)
  public void bufferOffer_shedding(Rings state) {
    state.saturated.put(state.event);
  }
}
